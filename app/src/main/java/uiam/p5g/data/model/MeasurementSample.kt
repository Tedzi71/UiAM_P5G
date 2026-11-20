package uiam.p5g.data.model

data class MeasurementSample(
    val id: Long = 0L,
    val sessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    // Location
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val accuracyMeters: Float = 0f,
    val bearingDegrees: Float = 0f,
    // Radio
    val networkType: String = "UNKNOWN",
    val operatorName: String = "",
    val mccMnc: String = "",
    val nrBand: String = "",
    val nrArfcn: Int = 0,
    val nrPci: Int = -1,
    val nrNci: Long = -1L,
    val nrSsRsrp: Int = -140,
    val nrSsRsrq: Int = -40,
    val nrSsSinr: Int = -23,
    val lteBand: String = "",
    val ltePci: Int = -1,
    val lteEarfcn: Int = 0,
    val lteRsrp: Int = -140,
    val lteRsrq: Int = -40,
    val lteSinr: Int = -23,
    // Gateway QoS
    val gatewayIp: String = "",
    val gatewayLatencyMs: Double = 0.0,
    val gatewayJitterMs: Double = 0.0,
    val packetLossPct: Double = 0.0,
    val probeMethod: String = "ICMP",
    // iperf3 Throughput
    val iperfDlMbps: Double = 0.0,
    val iperfUlMbps: Double = 0.0
) {
    val displayRsrp: Int
        get() = if (networkType.startsWith("5G") && nrSsRsrp > -140) nrSsRsrp else lteRsrp

    val displaySinr: Int
        get() = if (networkType.startsWith("5G") && nrSsSinr > -23) nrSsSinr else lteSinr

    val displayBand: String
        get() = if (networkType.startsWith("5G") && nrBand.isNotEmpty()) nrBand else lteBand

    val displayPci: Int
        get() = if (networkType.startsWith("5G") && nrPci >= 0) nrPci else ltePci
}
