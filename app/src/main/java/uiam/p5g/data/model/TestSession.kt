package uiam.p5g.data.model

data class TestSession(
    val id: Long = 0L,
    val name: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0L,
    val sampleCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val dominantNetworkType: String = "5G_SA",
    val avgRsrp: Double = 0.0,
    val avgSinr: Double = 0.0,
    val avgGatewayLatency: Double = 0.0,
    val maxDlThroughput: Double = 0.0,
    val notes: String = ""
)
