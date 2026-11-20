package uiam.p5g.data.model

data class QosMetrics(
    val gatewayIp: String = "",
    val isGatewayAutoDetected: Boolean = true,
    val rttAvgMs: Double = 0.0,
    val rttMinMs: Double = 0.0,
    val rttMaxMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val packetLossPct: Double = 0.0,
    val probeMethod: String = "ICMP", // "ICMP" or "TCP"
    val iperfDlMbps: Double = 0.0,
    val iperfUlMbps: Double = 0.0,
    val iperfStatus: String = "IDLE", // "IDLE", "TESTING", "SUCCESS", "UNREACHABLE", "ERROR"
    val iperfErrorMessage: String? = null
)
