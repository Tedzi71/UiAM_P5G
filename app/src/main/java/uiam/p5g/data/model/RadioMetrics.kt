package uiam.p5g.data.model

data class RadioMetrics(
    val networkType: String = "UNKNOWN", // 5G_SA, 5G_NSA, LTE, etc.
    val operatorName: String = "",
    val mccMnc: String = "",
    // 5G NR specific
    val nrBand: String = "",
    val nrArfcn: Int = 0,
    val nrPci: Int = -1,
    val nrNci: Long = -1L,
    val nrTac: Int = -1,
    val nrSsRsrp: Int = -140, // dBm
    val nrSsRsrq: Int = -40,  // dB
    val nrSsSinr: Int = -23,  // dB
    val nrCsiRsrp: Int = -140,
    val nrSignalLevel: Int = 0, // 0 to 4
    // 4G LTE anchor or fallback
    val lteBand: String = "",
    val lteEarfcn: Int = 0,
    val ltePci: Int = -1,
    val lteCi: Long = -1L,
    val lteTac: Int = -1,
    val lteRsrp: Int = -140,
    val lteRsrq: Int = -40,
    val lteRssnr: Int = -23,
    val lteSignalLevel: Int = 0
) {
    val is5G: Boolean
        get() = networkType.startsWith("5G")

    val primaryRsrp: Int
        get() = if (is5G && nrSsRsrp > -140) nrSsRsrp else lteRsrp

    val primarySinr: Int
        get() = if (is5G && nrSsSinr > -23) nrSsSinr else lteRssnr

    val primaryPci: Int
        get() = if (is5G && nrPci >= 0) nrPci else ltePci

    val primaryBand: String
        get() = if (is5G && nrBand.isNotEmpty()) nrBand else lteBand
}
