package uiam.p5g.telemetry

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import uiam.p5g.data.model.RadioMetrics

class RadioCollector(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @Volatile
    var currentMetrics = RadioMetrics()
        private set

    private var telephonyCallback: Any? = null
    private var isListening = false

    private fun hasPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return

        if (!hasPhonePermission() && !hasLocationPermission()) {
            Log.w("RadioCollector", "Permissions not yet granted; listening deferred")
            pollCurrentState()
            return
        }

        isListening = true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(),
                    TelephonyCallback.CellInfoListener,
                    TelephonyCallback.DisplayInfoListener,
                    TelephonyCallback.SignalStrengthsListener {

                    override fun onCellInfoChanged(cellInfoList: MutableList<CellInfo>) {
                        processCellInfo(cellInfoList)
                    }

                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        processDisplayInfo(telephonyDisplayInfo)
                    }

                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        processSignalStrengths(signalStrength)
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                telephonyCallback = callback
            }
        } catch (e: SecurityException) {
            Log.w("RadioCollector", "SecurityException during registerTelephonyCallback: ${e.message}")
            isListening = false
        } catch (e: Exception) {
            Log.w("RadioCollector", "Exception during registerTelephonyCallback: ${e.message}")
            isListening = false
        }

        // Query initial state
        pollCurrentState()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
                telephonyManager.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            }
        } catch (e: Exception) {
            Log.w("RadioCollector", "Exception during unregisterTelephonyCallback: ${e.message}")
        }
        telephonyCallback = null
    }

    @SuppressLint("MissingPermission")
    fun pollCurrentState(): RadioMetrics {
        var operator = ""
        var mccMnc = ""

        try {
            operator = telephonyManager.networkOperatorName ?: ""
            mccMnc = telephonyManager.networkOperator ?: ""
        } catch (e: Exception) {
            Log.w("RadioCollector", "Error querying operator info: ${e.message}")
        }

        var updated = currentMetrics.copy(
            operatorName = if (operator.isNotBlank()) operator else (if (isEmulator()) "Android Emulator" else "No Carrier"),
            mccMnc = mccMnc
        )

        if (hasLocationPermission()) {
            try {
                val cellInfoList = telephonyManager.allCellInfo
                if (!cellInfoList.isNullOrEmpty()) {
                    updated = extractMetricsFromCellList(cellInfoList, updated)
                } else if (isEmulator()) {
                    // Provide friendly mock metrics on emulator so UI displays meaningful preview
                    updated = updated.copy(
                        networkType = if (updated.networkType == "UNKNOWN") "5G_SA (Emulated)" else updated.networkType,
                        nrBand = if (updated.nrBand.isEmpty()) "n78 (3.5 GHz)" else updated.nrBand,
                        nrArfcn = if (updated.nrArfcn == 0) 640000 else updated.nrArfcn,
                        nrPci = if (updated.nrPci < 0) 104 else updated.nrPci,
                        nrNci = if (updated.nrNci < 0) 4123512L else updated.nrNci,
                        nrSsRsrp = if (updated.nrSsRsrp <= -140) -82 else updated.nrSsRsrp,
                        nrSsSinr = if (updated.nrSsSinr <= -23) 16 else updated.nrSsSinr,
                        nrSsRsrq = if (updated.nrSsRsrq <= -40) -11 else updated.nrSsRsrq
                    )
                }
            } catch (e: SecurityException) {
                Log.w("RadioCollector", "SecurityException during allCellInfo: ${e.message}")
            } catch (e: Exception) {
                Log.w("RadioCollector", "Exception during allCellInfo: ${e.message}")
            }
        } else if (isEmulator()) {
            updated = updated.copy(
                networkType = "5G_SA (Emulated)",
                nrBand = "n78 (3.5 GHz)",
                nrArfcn = 640000,
                nrPci = 104,
                nrNci = 4123512L,
                nrSsRsrp = -82,
                nrSsSinr = 16,
                nrSsRsrq = -11
            )
        }

        currentMetrics = updated
        return currentMetrics
    }

    private fun processCellInfo(cellInfoList: List<CellInfo>?) {
        if (cellInfoList.isNullOrEmpty()) return
        currentMetrics = extractMetricsFromCellList(cellInfoList, currentMetrics)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun processDisplayInfo(displayInfo: TelephonyDisplayInfo) {
        val overrideType = displayInfo.overrideNetworkType
        val netType = when (overrideType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G_NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G_NSA_ADV"
            else -> {
                if (currentMetrics.networkType.startsWith("5G")) {
                    currentMetrics.networkType
                } else {
                    val dataNet = getSafeDataNetworkType()
                    when (dataNet) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G_SA"
                        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                        else -> currentMetrics.networkType
                    }
                }
            }
        }
        currentMetrics = currentMetrics.copy(networkType = netType)
    }

    @SuppressLint("MissingPermission")
    private fun getSafeDataNetworkType(): Int {
        if (!hasPhonePermission()) return TelephonyManager.NETWORK_TYPE_UNKNOWN
        return try {
            telephonyManager.dataNetworkType
        } catch (e: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (e: Exception) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
    }

    private fun processSignalStrengths(signalStrength: SignalStrength) {
        // Raw signal strength
    }

    private fun extractMetricsFromCellList(
        cellInfoList: List<CellInfo>,
        base: RadioMetrics
    ): RadioMetrics {
        var nrInfo: CellInfoNr? = null
        var lteInfo: CellInfoLte? = null

        // Serving cell prioritized
        for (info in cellInfoList) {
            if (info is CellInfoNr && (info.isRegistered || nrInfo == null)) {
                nrInfo = info
            } else if (info is CellInfoLte && (info.isRegistered || lteInfo == null)) {
                lteInfo = info
            }
        }

        var updated = base

        // Extract 5G NR parameters
        if (nrInfo != null) {
            val identity = nrInfo.cellIdentity as? CellIdentityNr
            val strength = nrInfo.cellSignalStrength as? CellSignalStrengthNr

            var bandStr = ""
            var arfcn = 0
            var pci = -1
            var nci = -1L
            var tac = -1

            if (identity != null) {
                arfcn = identity.nrarfcn
                pci = identity.pci
                nci = identity.nci
                tac = identity.tac
                bandStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && identity.bands.isNotEmpty()) {
                    "n${identity.bands.first()}"
                } else {
                    estimateNrBand(arfcn)
                }
            }

            var ssRsrp = -140
            var ssRsrq = -40
            var ssSinr = -23
            var csiRsrp = -140
            var level = 0

            if (strength != null) {
                ssRsrp = strength.ssRsrp
                ssRsrq = strength.ssRsrq
                ssSinr = strength.ssSinr
                csiRsrp = strength.csiRsrp
                level = strength.level
            }

            val netType = if (base.networkType.contains("NSA")) base.networkType else "5G_SA"

            updated = updated.copy(
                networkType = netType,
                nrBand = bandStr,
                nrArfcn = arfcn,
                nrPci = pci,
                nrNci = nci,
                nrTac = tac,
                nrSsRsrp = ssRsrp,
                nrSsRsrq = ssRsrq,
                nrSsSinr = ssSinr,
                nrCsiRsrp = csiRsrp,
                nrSignalLevel = level
            )
        }

        // Extract LTE parameters (for NSA anchor or 4G)
        if (lteInfo != null) {
            val identity = lteInfo.cellIdentity as? CellIdentityLte
            val strength = lteInfo.cellSignalStrength as? CellSignalStrengthLte

            var lteBand = ""
            var earfcn = 0
            var pci = -1
            var ci = -1L
            var tac = -1

            if (identity != null) {
                earfcn = identity.earfcn
                pci = identity.pci
                ci = identity.ci.toLong()
                tac = identity.tac
                lteBand = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && identity.bands.isNotEmpty()) {
                    "B${identity.bands.first()}"
                } else {
                    estimateLteBand(earfcn)
                }
            }

            var rsrp = -140
            var rsrq = -40
            var rssnr = -23
            var level = 0

            if (strength != null) {
                rsrp = strength.rsrp
                rsrq = strength.rsrq
                rssnr = strength.rssnr
                level = strength.level
            }

            val netType = if (updated.networkType.startsWith("5G")) updated.networkType else "LTE"

            updated = updated.copy(
                networkType = netType,
                lteBand = lteBand,
                lteEarfcn = earfcn,
                ltePci = pci,
                lteCi = ci,
                lteTac = tac,
                lteRsrp = rsrp,
                lteRsrq = rsrq,
                lteRssnr = rssnr,
                lteSignalLevel = level
            )
        }

        return updated
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk_gphone"))
    }

    companion object {
        fun estimateNrBand(nrarfcn: Int): String {
            return when (nrarfcn) {
                in 620000..653333 -> "n78 (3.5 GHz)"
                in 620000..680000 -> "n77 (3.7 GHz)"
                in 422000..434000 -> "n1 (2.1 GHz)"
                in 361000..376000 -> "n3 (1.8 GHz)"
                in 500000..538000 -> "n41 (2.5 GHz)"
                in 151600..160600 -> "n28 (700 MHz)"
                in 405000..425000 -> "n8 (900 MHz)"
                in 2054166..2104165 -> "n258 (26 GHz mmWave)"
                in 2229166..2279165 -> "n257 (28 GHz mmWave)"
                else -> if (nrarfcn > 0) "ARFCN $nrarfcn" else ""
            }
        }

        fun estimateLteBand(earfcn: Int): String {
            return when (earfcn) {
                in 0..599 -> "B1 (2.1 GHz)"
                in 1200..1949 -> "B3 (1.8 GHz)"
                in 2750..3449 -> "B7 (2.6 GHz)"
                in 6000..6599 -> "B20 (800 MHz)"
                in 9210..9659 -> "B28 (700 MHz)"
                in 37750..38249 -> "B38 (2.6 GHz TDD)"
                else -> if (earfcn > 0) "EARFCN $earfcn" else ""
            }
        }
    }
}
