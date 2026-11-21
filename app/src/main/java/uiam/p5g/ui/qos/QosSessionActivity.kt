package uiam.p5g.ui.qos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uiam.p5g.R
import uiam.p5g.data.db.DatabaseManager
import uiam.p5g.data.model.MeasurementSample
import uiam.p5g.databinding.ActivityQosSessionBinding
import uiam.p5g.service.MeasurementService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QosSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQosSessionBinding
    private var measurementService: MeasurementService? = null
    private var isBound = false

    private lateinit var dbManager: DatabaseManager
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var timerJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MeasurementService.LocalBinder ?: return
            measurementService = binder.getService()
            isBound = true

            // If not recording yet, start service recording with a temporary name
            if (measurementService?.isRecording != true) {
                val tempName = "QoS_Session_${System.currentTimeMillis()}"
                measurementService?.startRecording(tempName)
            }

            observeSamples()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            measurementService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQosSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbManager = DatabaseManager.getInstance(this)

        // Handle camera cutout, top status bar, and bottom navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.qosSessionRoot) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(statusBars.left, statusBars.top, statusBars.right, navBars.bottom)
            windowInsets
        }

        setupButtons()
        setupBackHandler()
        startElapsedTimer()
        bindService()
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            handleExitRequest()
        }

        binding.btnStopAndSave.setOnClickListener {
            promptSaveSessionName()
        }

        binding.btnExitWithoutSaving.setOnClickListener {
            confirmExitWithoutSaving()
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })
    }

    private fun handleExitRequest() {
        if (measurementService?.isRecording == true) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Active Session in Progress")
                .setMessage("A measurement session is currently recording. Would you like to save it or discard?")
                .setPositiveButton("Save") { _, _ ->
                    promptSaveSessionName()
                }
                .setNegativeButton("Discard") { _, _ ->
                    exitAndDiscard()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            finish()
        }
    }

    private fun bindService() {
        val intent = Intent(this, MeasurementService::class.java).apply {
            action = MeasurementService.ACTION_START
            putExtra(MeasurementService.EXTRA_SESSION_NAME, "QoS_Session_${System.currentTimeMillis()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeSamples() {
        lifecycleScope.launch {
            measurementService?.liveSampleFlow?.collect { sample ->
                renderSample(sample)
            }
        }
    }

    private fun renderSample(s: MeasurementSample) {
        // 1. RF
        binding.tvNetType.text = s.networkType
        val badgeColor = when {
            s.networkType.startsWith("5G_SA") -> ContextCompat.getColor(this, R.color.tech_5g_sa)
            s.networkType.contains("NSA") -> ContextCompat.getColor(this, R.color.tech_5g_nsa)
            else -> ContextCompat.getColor(this, R.color.tech_lte)
        }
        binding.tvNetType.background?.mutate()?.setTint(badgeColor)

        val op = if (s.operatorName.isNotEmpty()) s.operatorName else "Carrier"
        val band = if (s.displayBand.isNotEmpty()) s.displayBand else "Band ---"
        binding.tvOperatorAndBand.text = "$op | Band: $band"

        val rsrp = s.displayRsrp
        binding.tvRsrp.text = if (rsrp > -140) "$rsrp dBm" else "-- dBm"
        binding.tvRsrp.setTextColor(getRsrpColor(rsrp))

        val sinr = s.displaySinr
        binding.tvSinr.text = if (sinr > -23) "$sinr dB" else "-- dB"
        binding.tvSinr.setTextColor(getSinrColor(sinr))

        val pciStr = if (s.displayPci >= 0) "${s.displayPci}" else "--"
        val arfcn = if (s.networkType.startsWith("5G")) s.nrArfcn else s.lteEarfcn
        val arfcnStr = if (arfcn > 0) "$arfcn" else "--"
        binding.tvPciArfcn.text = "$pciStr / $arfcnStr"

        // 2. Gateway QoS
        binding.tvGatewayTarget.text = if (s.gatewayIp.isNotEmpty()) "GW: ${s.gatewayIp}" else "GW: Detecting..."

        val lat = s.gatewayLatencyMs
        binding.tvLatency.text = if (lat > 0) String.format(Locale.US, "%.1f ms", lat) else "-- ms"
        binding.tvLatency.setTextColor(getLatencyColor(lat))

        val jitter = s.gatewayJitterMs
        binding.tvJitter.text = if (jitter > 0) String.format(Locale.US, "%.1f ms", jitter) else "-- ms"

        binding.tvPacketLoss.text = String.format(Locale.US, "%.1f%%", s.packetLossPct)
        binding.tvPacketLoss.setTextColor(
            if (s.packetLossPct > 0) ContextCompat.getColor(this, R.color.color_bad)
            else ContextCompat.getColor(this, R.color.color_excellent)
        )

        // 3. GPS
        if (s.latitude != 0.0 || s.longitude != 0.0) {
            binding.tvGpsCoords.text = String.format(Locale.US, "Lat: %.5f, Lon: %.5f (Alt: %.0f m)", s.latitude, s.longitude, s.altitude)
            binding.tvGpsSpeedAcc.text = String.format(Locale.US, "Speed: %.1f km/h | Acc: %.0f m | Bearing: %.0f°", s.speedKmh, s.accuracyMeters, s.bearingDegrees)
        } else {
            binding.tvGpsCoords.text = "Lat: Waiting for GPS..., Lon: --"
            binding.tvGpsSpeedAcc.text = "Speed: 0.0 km/h | Acc: -- m"
        }

        // Stats
        measurementService?.let { svc ->
            binding.tvSampleCounter.text = "${svc.sampleCount} Samples Recorded"
        }
    }

    private fun promptSaveSessionName() {
        val defaultName = "5G_Session_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
            isSingleLine = true
        }

        val container = android.widget.FrameLayout(this).apply {
            val paddingHorizontal = (24 * resources.displayMetrics.density).toInt()
            val paddingTop = (8 * resources.displayMetrics.density).toInt()
            setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Save QoS Test Session")
            .setMessage("Enter a name for this drive/walk test session:")
            .setView(container)
            .setPositiveButton("Save Session") { _, _ ->
                val chosenName = input.text.toString().trim().ifEmpty { defaultName }
                saveAndFinish(chosenName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAndFinish(sessionName: String) {
        val sessionId = measurementService?.currentSessionId ?: -1L
        try {
            // Stop recording service
            measurementService?.stopRecording()

            // Update session name in SQLite database
            if (sessionId > 0) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = dbManager.writableDatabase
                    val values = android.content.ContentValues().apply {
                        put(DatabaseManager.COL_SESSION_NAME, sessionName)
                    }
                    db.update(DatabaseManager.TABLE_SESSIONS, values, "${DatabaseManager.COL_SESSION_ID} = ?", arrayOf(sessionId.toString()))

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@QosSessionActivity, "Session '$sessionName' saved!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } else {
                Toast.makeText(this, "Session saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("QosSessionActivity", "Error saving session: ${e.message}", e)
            finish()
        }
    }

    private fun confirmExitWithoutSaving() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Session?")
            .setMessage("Are you sure you want to exit without saving? All telemetry samples recorded in this session will be permanently deleted.")
            .setPositiveButton("Discard & Exit") { _, _ ->
                exitAndDiscard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exitAndDiscard() {
        val sessionId = measurementService?.currentSessionId ?: -1L
        try {
            measurementService?.stopRecording()
            if (sessionId > 0) {
                lifecycleScope.launch(Dispatchers.IO) {
                    dbManager.deleteSession(sessionId)
                }
            }
        } catch (e: Exception) {
            Log.w("QosSessionActivity", "Error discarding session: ${e.message}")
        }
        Toast.makeText(this, "Session discarded", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startElapsedTimer() {
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsedSec = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                binding.tvElapsedTime.text = String.format(Locale.US, "Elapsed: %02d:%02d", elapsedSec / 60, elapsedSec % 60)
                delay(1000L)
            }
        }
    }

    private fun getRsrpColor(rsrp: Int): Int {
        return when {
            rsrp >= -80 -> ContextCompat.getColor(this, R.color.color_excellent)
            rsrp >= -90 -> ContextCompat.getColor(this, R.color.color_good)
            rsrp >= -100 -> ContextCompat.getColor(this, R.color.color_mid)
            rsrp >= -110 -> ContextCompat.getColor(this, R.color.color_poor)
            else -> ContextCompat.getColor(this, R.color.color_bad)
        }
    }

    private fun getSinrColor(sinr: Int): Int {
        return when {
            sinr >= 15 -> ContextCompat.getColor(this, R.color.color_excellent)
            sinr >= 5 -> ContextCompat.getColor(this, R.color.color_good)
            sinr >= 0 -> ContextCompat.getColor(this, R.color.color_mid)
            else -> ContextCompat.getColor(this, R.color.color_bad)
        }
    }

    private fun getLatencyColor(latencyMs: Double): Int {
        return when {
            latencyMs <= 0 -> ContextCompat.getColor(this, R.color.text_secondary_dark)
            latencyMs < 15.0 -> ContextCompat.getColor(this, R.color.color_excellent)
            latencyMs < 30.0 -> ContextCompat.getColor(this, R.color.color_good)
            latencyMs < 60.0 -> ContextCompat.getColor(this, R.color.color_mid)
            else -> ContextCompat.getColor(this, R.color.color_bad)
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w("QosSessionActivity", "Error unbinding: ${e.message}")
            }
            isBound = false
        }
        super.onDestroy()
    }
}
