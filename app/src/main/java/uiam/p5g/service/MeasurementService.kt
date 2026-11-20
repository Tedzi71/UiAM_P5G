package uiam.p5g.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uiam.p5g.R
import uiam.p5g.data.db.DatabaseManager
import uiam.p5g.data.model.MeasurementSample
import uiam.p5g.data.preferences.SettingsManager
import uiam.p5g.location.LocationProvider
import uiam.p5g.qos.GatewayDetector
import uiam.p5g.qos.GatewayPingProbe
import uiam.p5g.qos.IperfClient
import uiam.p5g.telemetry.RadioCollector
import uiam.p5g.ui.MainActivity

class MeasurementService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var dbManager: DatabaseManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var radioCollector: RadioCollector
    private lateinit var locationProvider: LocationProvider
    private lateinit var gatewayDetector: GatewayDetector
    private val pingProbe = GatewayPingProbe()
    private val iperfClient = IperfClient()

    private var wakeLock: PowerManager.WakeLock? = null
    private var collectionJob: Job? = null

    var currentSessionId: Long = -1L
        private set
    var currentSessionName: String = ""
        private set
    var sampleCount: Int = 0
        private set
    var isRecording: Boolean = false
        private set

    private val _liveSampleFlow = MutableSharedFlow<MeasurementSample>(replay = 1)
    val liveSampleFlow: SharedFlow<MeasurementSample> = _liveSampleFlow

    inner class LocalBinder : Binder() {
        fun getService(): MeasurementService = this@MeasurementService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        dbManager = DatabaseManager.getInstance(this)
        settingsManager = SettingsManager.getInstance(this)
        radioCollector = RadioCollector(this)
        locationProvider = LocationProvider(this)
        gatewayDetector = GatewayDetector(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "P5G::MeasurementWakeLock")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Drive_Test_${System.currentTimeMillis()}"
                startRecording(sessionName)
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    fun startRecording(sessionName: String) {
        if (isRecording) return
        isRecording = true
        currentSessionName = sessionName
        sampleCount = 0

        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
        currentSessionId = dbManager.createSession(sessionName)

        radioCollector.startListening()
        locationProvider.startTracking(minTimeMs = 1000L, minDistanceMeters = 0f)

        startForegroundNotification()

        collectionJob = serviceScope.launch {
            var lastThroughputTime = System.currentTimeMillis()
            var cachedDlMbps = 0.0
            var cachedUlMbps = 0.0

            while (isActive && isRecording) {
                val loopStart = System.currentTimeMillis()

                // 1. Radio state
                val radio = radioCollector.pollCurrentState()

                // 2. Location
                val location = locationProvider.lastKnownLocation

                // 3. Gateway Target
                val targetIp = if (settingsManager.isGatewayAuto) {
                    val gwInfo = gatewayDetector.detectDefaultGateway()
                    gwInfo.gatewayIp ?: settingsManager.manualGatewayIp
                } else {
                    settingsManager.manualGatewayIp
                }

                // 4. Latency / Jitter Probe
                val probeResult = pingProbe.executeProbe(
                    targetIp = targetIp,
                    tcpFallbackPort = settingsManager.gatewayTcpPort,
                    probeCount = 2
                )

                // 5. Periodic Throughput (if configured)
                val autoThroughputInterval = settingsManager.throughputAutoIntervalSec
                if (autoThroughputInterval > 0 &&
                    (System.currentTimeMillis() - lastThroughputTime) >= autoThroughputInterval * 1000L
                ) {
                    val iperfResult = iperfClient.runTest(
                        serverIp = settingsManager.iperfServerIp,
                        serverPort = settingsManager.iperfServerPort,
                        isDownload = true,
                        durationSec = 5
                    )
                    cachedDlMbps = iperfResult.dlThroughputMbps
                    cachedUlMbps = iperfResult.ulThroughputMbps
                    lastThroughputTime = System.currentTimeMillis()
                }

                // 6. Build Sample
                val sample = MeasurementSample(
                    sessionId = currentSessionId,
                    timestamp = System.currentTimeMillis(),
                    latitude = location?.latitude ?: 0.0,
                    longitude = location?.longitude ?: 0.0,
                    altitude = location?.altitude ?: 0.0,
                    speedKmh = (location?.speed ?: 0f) * 3.6f,
                    accuracyMeters = location?.accuracy ?: 0f,
                    bearingDegrees = location?.bearing ?: 0f,
                    networkType = radio.networkType,
                    operatorName = radio.operatorName,
                    mccMnc = radio.mccMnc,
                    nrBand = radio.nrBand,
                    nrArfcn = radio.nrArfcn,
                    nrPci = radio.nrPci,
                    nrNci = radio.nrNci,
                    nrSsRsrp = radio.nrSsRsrp,
                    nrSsRsrq = radio.nrSsRsrq,
                    nrSsSinr = radio.nrSsSinr,
                    lteBand = radio.lteBand,
                    ltePci = radio.ltePci,
                    lteEarfcn = radio.lteEarfcn,
                    lteRsrp = radio.lteRsrp,
                    lteRsrq = radio.lteRsrq,
                    lteSinr = radio.lteRssnr,
                    gatewayIp = targetIp,
                    gatewayLatencyMs = probeResult.rttAvgMs,
                    gatewayJitterMs = probeResult.jitterMs,
                    packetLossPct = probeResult.packetLossPct,
                    probeMethod = probeResult.probeMethod,
                    iperfDlMbps = cachedDlMbps,
                    iperfUlMbps = cachedUlMbps
                )

                dbManager.insertSample(sample)
                sampleCount++
                _liveSampleFlow.emit(sample)

                updateNotification(sample)

                val elapsed = System.currentTimeMillis() - loopStart
                val intervalMs = settingsManager.sampleIntervalSec * 1000L
                val sleepTime = (intervalMs - elapsed).coerceAtLeast(200L)
                delay(sleepTime)
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        collectionJob?.cancel()
        collectionJob = null

        radioCollector.stopListening()
        locationProvider.stopTracking()

        if (currentSessionId > 0) {
            dbManager.closeSession(currentSessionId)
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("Starting measurement session...", "Preparing 5G telemetry")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(sample: MeasurementSample) {
        val title = "5G Drive Test: $currentSessionName"
        val rsrpStr = if (sample.displayRsrp > -140) "${sample.displayRsrp} dBm" else "N/A"
        val latStr = if (sample.gatewayLatencyMs > 0) "${sample.gatewayLatencyMs} ms" else "N/A"
        val text = "${sample.networkType} | RSRP: $rsrpStr | GW: $latStr | Samples: $sampleCount"

        val notification = buildNotification(title, text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MeasurementService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Test", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "5G Drive Test Logging",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background recording of 5G cellular radio and gateway QoS telemetry"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "uiam.p5g.action.START"
        const val ACTION_STOP = "uiam.p5g.action.STOP"
        const val EXTRA_SESSION_NAME = "extra_session_name"
        private const val CHANNEL_ID = "p5g_measurement_channel"
        private const val NOTIFICATION_ID = 5001
    }
}
