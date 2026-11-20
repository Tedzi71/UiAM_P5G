package uiam.p5g.telemetry

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.annotation.RequiresApi
import uiam.p5g.data.model.RadioMetrics

class RadioCollector(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @Volatile
    var currentMetrics = RadioMetrics()
        private set

    private var telephonyCallback: Any? = null
    private var isListening = false

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return
        isListening = true

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

        // Query initial state
        pollCurrentState()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
        }
        telephonyCallback = null
    }

    @SuppressLint("MissingPermission")
    fun pollCurrentState(): RadioMetrics {
        val operator = telephonyManager.networkOperatorName ?: ""
        val mccMnc = telephonyManager.networkOperator ?: ""

        var updated = currentMetrics.copy(
            operatorName = operator,
            mccMnc = mccMnc
        )

        try {
            val cellInfoList = telephonyManager.allCellInfo
            if (!cellInfoList.isNullOrEmpty()) {
                updated = extractMetricsFromCellList(cellInfoList, updated)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

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
                } else if (telephonyManager.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR) {
                    "5G_SA"
                } else if (telephonyManager.dataNetworkType == TelephonyManager.NETWORK_TYPE_LTE) {
                    "LTE"
                } else {
                    currentMetrics.networkType
                }
            }
        }
        currentMetrics = currentMetrics.copy(networkType = netType)
    }

    private fun processSignalStrengths(signalStrength: SignalStrength) {
        // Raw signal strength callback can be polled as needed
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
