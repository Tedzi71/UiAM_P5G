package uiam.p5g.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import uiam.p5g.R
import uiam.p5g.data.db.DatabaseManager
import uiam.p5g.data.model.MeasurementSample
import uiam.p5g.data.model.TestSession
import uiam.p5g.databinding.ActivityMapBinding
import uiam.p5g.databinding.DialogCustomThresholdsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var dbManager: DatabaseManager

    private var currentSession: TestSession? = null
    private var currentSamples: List<MeasurementSample> = emptyList()
    private var currentMetricIndex: Int = 0 // 0: RSRP, 1: SINR, 2: Gateway Latency, 3: Throughput, 4: Tech

    // Custom Thresholds (stored in memory / can be tuned via dialog)
    private var rsrpExcellentThresh = -80
    private var rsrpMidThresh = -100
    private var rsrpPoorThresh = -110

    private var latExcellentThresh = 15.0
    private var latMidThresh = 30.0
    private var latPoorThresh = 60.0

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbManager = DatabaseManager.getInstance(this)

        // Handle camera cutout, top status bar, and bottom navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.mapActivityRoot) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(statusBars.left, statusBars.top, statusBars.right, navBars.bottom)
            windowInsets
        }

        setupMapView()
        setupTopBar()
        setupListeners()

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId > 0) {
            loadSessionData(sessionId)
        } else {
            Toast.makeText(this, "No session selected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupMapView() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnOptionsMenu.setOnClickListener { view ->
            showOptionsMenu(view)
        }
    }

    private fun setupListeners() {
        binding.btnClosePopup.setOnClickListener {
            binding.cardTelemetryPopup.visibility = View.GONE
        }

        binding.fabFitTrack.setOnClickListener {
            fitTrackToBounds()
        }
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (currentMetricIndex == 0) "✓ Metric: SS-RSRP Coverage" else "Metric: SS-RSRP Coverage")
        popup.menu.add(0, 2, 1, if (currentMetricIndex == 1) "✓ Metric: SS-SINR Quality" else "Metric: SS-SINR Quality")
        popup.menu.add(0, 3, 2, if (currentMetricIndex == 2) "✓ Metric: 5G Gateway Latency" else "Metric: 5G Gateway Latency")
        popup.menu.add(0, 4, 3, if (currentMetricIndex == 3) "✓ Metric: iperf3 Throughput" else "Metric: iperf3 Throughput")
        popup.menu.add(0, 5, 4, if (currentMetricIndex == 4) "✓ Metric: 5G Network Type" else "Metric: 5G Network Type")
        popup.menu.add(0, 6, 5, "⚙️ Change Color Thresholds")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> switchMetric(0, "SS-RSRP Coverage")
                2 -> switchMetric(1, "SS-SINR Quality")
                3 -> switchMetric(2, "5G Gateway Latency")
                4 -> switchMetric(3, "iperf3 Throughput")
                5 -> switchMetric(4, "5G Network Type")
                6 -> showThresholdCustomizationDialog()
            }
            true
        }
        popup.show()
    }

    private fun switchMetric(index: Int, name: String) {
        currentMetricIndex = index
        binding.tvCurrentMetricSubtitle.text = "Layer: $name"
        updateVerticalLegend()
        renderTrackOnMap()
    }

    private fun loadSessionData(sessionId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            currentSession = dbManager.getSessionById(sessionId)
            val samples = dbManager.getSamplesForSession(sessionId)

            withContext(Dispatchers.Main) {
                currentSamples = samples.filter { it.latitude != 0.0 || it.longitude != 0.0 }
                val sessionName = currentSession?.name ?: "Session #$sessionId"
                binding.tvSessionTitle.text = "$sessionName (${currentSamples.size} pts)"

                updateVerticalLegend()
                renderTrackOnMap()
                fitTrackToBounds()
            }
        }
    }

    private fun renderTrackOnMap() {
        binding.mapView.overlays.clear()
        binding.cardTelemetryPopup.visibility = View.GONE

        if (currentSamples.isEmpty()) {
            binding.mapView.invalidate()
            return
        }

        val geoPoints = ArrayList<GeoPoint>()

        for (sample in currentSamples) {
            val point = GeoPoint(sample.latitude, sample.longitude)
            geoPoints.add(point)

            val color = getColorForSample(sample)
            val marker = Marker(binding.mapView).apply {
                position = point
                icon = createCircleDrawable(color, 24)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    displayTelemetryPopup(sample)
                    true
                }
            }
            binding.mapView.overlays.add(marker)
        }

        // Connecting route polyline
        if (geoPoints.size >= 2) {
            val polyline = Polyline(binding.mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = Color.parseColor("#4400E5FF")
                outlinePaint.strokeWidth = 6f
            }
            binding.mapView.overlays.add(0, polyline)
        }

        binding.mapView.invalidate()
    }

    private fun getColorForSample(s: MeasurementSample): Int {
        return when (currentMetricIndex) {
            0 -> { // SS-RSRP
                val rsrp = s.displayRsrp
                when {
                    rsrp >= rsrpExcellentThresh -> ContextCompat.getColor(this, R.color.color_excellent)
                    rsrp >= (rsrpExcellentThresh + rsrpMidThresh) / 2 -> ContextCompat.getColor(this, R.color.color_good)
                    rsrp >= rsrpMidThresh -> ContextCompat.getColor(this, R.color.color_mid)
                    rsrp >= rsrpPoorThresh -> ContextCompat.getColor(this, R.color.color_poor)
                    else -> ContextCompat.getColor(this, R.color.color_bad)
                }
            }
            1 -> { // SS-SINR
                val sinr = s.displaySinr
                when {
                    sinr >= 15 -> ContextCompat.getColor(this, R.color.color_excellent)
                    sinr >= 5 -> ContextCompat.getColor(this, R.color.color_good)
                    sinr >= 0 -> ContextCompat.getColor(this, R.color.color_mid)
                    else -> ContextCompat.getColor(this, R.color.color_bad)
                }
            }
            2 -> { // 5G Gateway Latency
                val lat = s.gatewayLatencyMs
                when {
                    lat <= 0 -> Color.GRAY
                    lat <= latExcellentThresh -> ContextCompat.getColor(this, R.color.color_excellent)
                    lat <= latMidThresh -> ContextCompat.getColor(this, R.color.color_good)
                    lat <= latPoorThresh -> ContextCompat.getColor(this, R.color.color_mid)
                    else -> ContextCompat.getColor(this, R.color.color_bad)
                }
            }
            3 -> { // iperf3 Throughput
                val dl = s.iperfDlMbps
                when {
                    dl >= 100 -> ContextCompat.getColor(this, R.color.color_excellent)
                    dl >= 25 -> ContextCompat.getColor(this, R.color.color_good)
                    dl >= 10 -> ContextCompat.getColor(this, R.color.color_mid)
                    dl > 0 -> ContextCompat.getColor(this, R.color.color_poor)
                    else -> Color.GRAY
                }
            }
            4 -> { // 5G Network Type
                when (s.networkType) {
                    "5G_SA" -> ContextCompat.getColor(this, R.color.tech_5g_sa)
                    "5G_NSA", "5G_NSA_ADV" -> ContextCompat.getColor(this, R.color.tech_5g_nsa)
                    else -> ContextCompat.getColor(this, R.color.tech_lte)
                }
            }
            else -> ContextCompat.getColor(this, R.color.color_excellent)
        }
    }

    private fun updateVerticalLegend() {
        when (currentMetricIndex) {
            0 -> {
                binding.tvLegendMetricName.text = "SS-RSRP (dBm)"
                binding.tvLegendVal1.text = "> $rsrpExcellentThresh"
                binding.tvLegendVal2.text = "${(rsrpExcellentThresh + rsrpMidThresh) / 2}"
                binding.tvLegendVal3.text = "$rsrpMidThresh"
                binding.tvLegendVal4.text = "$rsrpPoorThresh"
                binding.tvLegendVal5.text = "< $rsrpPoorThresh"

                binding.viewColor1.background.setTint(ContextCompat.getColor(this, R.color.color_excellent))
                binding.viewColor2.background.setTint(ContextCompat.getColor(this, R.color.color_good))
                binding.viewColor3.background.setTint(ContextCompat.getColor(this, R.color.color_mid))
                binding.viewColor4.background.setTint(ContextCompat.getColor(this, R.color.color_poor))
                binding.viewColor5.background.setTint(ContextCompat.getColor(this, R.color.color_bad))
            }
            1 -> {
                binding.tvLegendMetricName.text = "SS-SINR (dB)"
                binding.tvLegendVal1.text = "> 15"
                binding.tvLegendVal2.text = "10"
                binding.tvLegendVal3.text = "5"
                binding.tvLegendVal4.text = "0"
                binding.tvLegendVal5.text = "< 0"

                binding.viewColor1.background.setTint(ContextCompat.getColor(this, R.color.color_excellent))
                binding.viewColor2.background.setTint(ContextCompat.getColor(this, R.color.color_good))
                binding.viewColor3.background.setTint(ContextCompat.getColor(this, R.color.color_mid))
                binding.viewColor4.background.setTint(ContextCompat.getColor(this, R.color.color_poor))
                binding.viewColor5.background.setTint(ContextCompat.getColor(this, R.color.color_bad))
            }
            2 -> {
                binding.tvLegendMetricName.text = "GW Lat (ms)"
                binding.tvLegendVal1.text = "< ${latExcellentThresh.toInt()}"
                binding.tvLegendVal2.text = "${((latExcellentThresh + latMidThresh) / 2).toInt()}"
                binding.tvLegendVal3.text = "${latMidThresh.toInt()}"
                binding.tvLegendVal4.text = "${latPoorThresh.toInt()}"
                binding.tvLegendVal5.text = "> ${latPoorThresh.toInt()}"

                binding.viewColor1.background.setTint(ContextCompat.getColor(this, R.color.color_excellent))
                binding.viewColor2.background.setTint(ContextCompat.getColor(this, R.color.color_good))
                binding.viewColor3.background.setTint(ContextCompat.getColor(this, R.color.color_mid))
                binding.viewColor4.background.setTint(ContextCompat.getColor(this, R.color.color_poor))
                binding.viewColor5.background.setTint(ContextCompat.getColor(this, R.color.color_bad))
            }
            3 -> {
                binding.tvLegendMetricName.text = "DL (Mbps)"
                binding.tvLegendVal1.text = "> 100"
                binding.tvLegendVal2.text = "50"
                binding.tvLegendVal3.text = "25"
                binding.tvLegendVal4.text = "10"
                binding.tvLegendVal5.text = "< 10"

                binding.viewColor1.background.setTint(ContextCompat.getColor(this, R.color.color_excellent))
                binding.viewColor2.background.setTint(ContextCompat.getColor(this, R.color.color_good))
                binding.viewColor3.background.setTint(ContextCompat.getColor(this, R.color.color_mid))
                binding.viewColor4.background.setTint(ContextCompat.getColor(this, R.color.color_poor))
                binding.viewColor5.background.setTint(ContextCompat.getColor(this, R.color.color_bad))
            }
            4 -> {
                binding.tvLegendMetricName.text = "5G Tech"
                binding.tvLegendVal1.text = "5G SA"
                binding.tvLegendVal2.text = "5G NSA"
                binding.tvLegendVal3.text = "LTE"
                binding.tvLegendVal4.text = "--"
                binding.tvLegendVal5.text = "--"

                binding.viewColor1.background.setTint(ContextCompat.getColor(this, R.color.tech_5g_sa))
                binding.viewColor2.background.setTint(ContextCompat.getColor(this, R.color.tech_5g_nsa))
                binding.viewColor3.background.setTint(ContextCompat.getColor(this, R.color.tech_lte))
                binding.viewColor4.background.setTint(Color.TRANSPARENT)
                binding.viewColor5.background.setTint(Color.TRANSPARENT)
            }
        }
    }

    private fun showThresholdCustomizationDialog() {
        val dialogBinding = DialogCustomThresholdsBinding.inflate(layoutInflater)
        dialogBinding.etRsrpExcellent.setText(rsrpExcellentThresh.toString())
        dialogBinding.etRsrpMid.setText(rsrpMidThresh.toString())
        dialogBinding.etRsrpPoor.setText(rsrpPoorThresh.toString())

        dialogBinding.etLatExcellent.setText(latExcellentThresh.toInt().toString())
        dialogBinding.etLatMid.setText(latMidThresh.toInt().toString())
        dialogBinding.etLatPoor.setText(latPoorThresh.toInt().toString())

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Apply Thresholds") { _, _ ->
                rsrpExcellentThresh = dialogBinding.etRsrpExcellent.text.toString().toIntOrNull() ?: rsrpExcellentThresh
                rsrpMidThresh = dialogBinding.etRsrpMid.text.toString().toIntOrNull() ?: rsrpMidThresh
                rsrpPoorThresh = dialogBinding.etRsrpPoor.text.toString().toIntOrNull() ?: rsrpPoorThresh

                latExcellentThresh = dialogBinding.etLatExcellent.text.toString().toDoubleOrNull() ?: latExcellentThresh
                latMidThresh = dialogBinding.etLatMid.text.toString().toDoubleOrNull() ?: latMidThresh
                latPoorThresh = dialogBinding.etLatPoor.text.toString().toDoubleOrNull() ?: latPoorThresh

                updateVerticalLegend()
                renderTrackOnMap()
                Toast.makeText(this, "Updated thresholds applied", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Reset Defaults") { _, _ ->
                rsrpExcellentThresh = -80
                rsrpMidThresh = -100
                rsrpPoorThresh = -110

                latExcellentThresh = 15.0
                latMidThresh = 30.0
                latPoorThresh = 60.0

                updateVerticalLegend()
                renderTrackOnMap()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayTelemetryPopup(s: MeasurementSample) {
        binding.tvPopupTitle.text = "Sample #${s.id} Telemetry"
        binding.tvPopupTimestampCoords.text = String.format(
            Locale.US,
            "%s | Lat: %.5f, Lon: %.5f (%.0f m) | %.1f km/h",
            timeFormat.format(Date(s.timestamp)),
            s.latitude,
            s.longitude,
            s.altitude,
            s.speedKmh
        )

        val bandStr = if (s.displayBand.isNotEmpty()) s.displayBand else "Band ---"
        val pciStr = if (s.displayPci >= 0) "PCI: ${s.displayPci}" else "PCI: ---"
        val nciStr = if (s.nrNci > 0) "NCI: ${s.nrNci}" else "NCI: ---"
        binding.tvPopupRadioDetails.text = "${s.networkType} | $bandStr | $pciStr | $nciStr"

        val rsrpStr = if (s.displayRsrp > -140) "${s.displayRsrp} dBm" else "N/A"
        val sinrStr = if (s.displaySinr > -23) "${s.displaySinr} dB" else "N/A"
        binding.tvPopupSignalDetails.text = "SS-RSRP: $rsrpStr | SS-SINR: $sinrStr | RSRQ: ${s.nrSsRsrq} dB"

        val gwLat = if (s.gatewayLatencyMs > 0) "${s.gatewayLatencyMs} ms" else "N/A"
        val gwJitter = if (s.gatewayJitterMs > 0) "${s.gatewayJitterMs} ms" else "N/A"
        binding.tvPopupQosDetails.text = "5G GW (${s.gatewayIp}): RTT $gwLat | Jitter $gwJitter | Loss ${s.packetLossPct}%"

        binding.cardTelemetryPopup.visibility = View.VISIBLE
    }

    private fun fitTrackToBounds() {
        if (currentSamples.isEmpty()) return

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (s in currentSamples) {
            if (s.latitude < minLat) minLat = s.latitude
            if (s.latitude > maxLat) maxLat = s.latitude
            if (s.longitude < minLon) minLon = s.longitude
            if (s.longitude > maxLon) maxLon = s.longitude
        }

        if (minLat < maxLat && minLon < maxLon) {
            val boundingBox = BoundingBox(maxLat + 0.001, maxLon + 0.001, minLat - 0.001, minLon - 0.001)
            binding.mapView.zoomToBoundingBox(boundingBox, true, 80)
        } else if (currentSamples.isNotEmpty()) {
            binding.mapView.controller.animateTo(GeoPoint(currentSamples[0].latitude, currentSamples[0].longitude))
            binding.mapView.controller.setZoom(16.0)
        }
    }

    private fun createCircleDrawable(color: Int, sizeDp: Int): BitmapDrawable {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Outer white border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }

        // Fill color
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        val radius = (sizePx / 2f) - (2f * density)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, fillPaint)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, borderPaint)

        return BitmapDrawable(resources, bitmap)
    }

    override fun onResume() {
        super.onResume()
        try {
            binding.mapView.onResume()
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            binding.mapView.onPause()
        } catch (_: Exception) {}
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
