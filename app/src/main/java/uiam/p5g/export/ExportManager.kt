package uiam.p5g.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import uiam.p5g.data.model.MeasurementSample
import uiam.p5g.data.model.TestSession
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    data class ExportResult(
        val isSuccess: Boolean,
        val filePath: String,
        val uri: Uri?,
        val errorMessage: String? = null
    )

    fun exportToCsv(session: TestSession, samples: List<MeasurementSample>): ExportResult {
        return try {
            val fileName = "P5G_Session_${session.id}_${fileDateFormat.format(Date(session.startTime))}.csv"
            val file = getExportFile(fileName)

            FileWriter(file).use { writer ->
                // Write CSV Header
                writer.appendLine(
                    "Sample_ID,Session_ID,Timestamp_Epoch,DateTime_UTC,Latitude,Longitude,Altitude_m," +
                            "Speed_kmh,Accuracy_m,Bearing_deg,Network_Type,Operator,MCC_MNC,NR_Band," +
                            "NR_ARFCN,NR_PCI,NR_NCI,SS_RSRP_dBm,SS_RSRQ_dB,SS_SINR_dB,LTE_Band,LTE_PCI," +
                            "LTE_EARFCN,LTE_RSRP_dBm,LTE_RSRQ_dB,LTE_SINR_dB,Gateway_IP,Gateway_Latency_ms," +
                            "Gateway_Jitter_ms,Packet_Loss_pct,Probe_Method,iperf_DL_Mbps,iperf_UL_Mbps"
                )

                for (s in samples) {
                    val dateStr = dateFormat.format(Date(s.timestamp))
                    writer.appendLine(
                        "${s.id},${s.sessionId},${s.timestamp},$dateStr,${s.latitude},${s.longitude},${s.altitude}," +
                                "${s.speedKmh},${s.accuracyMeters},${s.bearingDegrees},${s.networkType},\"${s.operatorName}\"," +
                                "${s.mccMnc},${s.nrBand},${s.nrArfcn},${s.nrPci},${s.nrNci},${s.nrSsRsrp},${s.nrSsRsrq}," +
                                "${s.nrSsSinr},${s.lteBand},${s.ltePci},${s.lteEarfcn},${s.lteRsrp},${s.lteRsrq}," +
                                "${s.lteSinr},${s.gatewayIp},${s.gatewayLatencyMs},${s.gatewayJitterMs},${s.packetLossPct}," +
                                "${s.probeMethod},${s.iperfDlMbps},${s.iperfUlMbps}"
                    )
                }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            ExportResult(true, file.absolutePath, uri)
        } catch (e: Exception) {
            ExportResult(false, "", null, e.message)
        }
    }

    fun exportToGeoJson(session: TestSession, samples: List<MeasurementSample>): ExportResult {
        return try {
            val fileName = "P5G_Session_${session.id}_${fileDateFormat.format(Date(session.startTime))}.geojson"
            val file = getExportFile(fileName)

            FileWriter(file).use { writer ->
                writer.appendLine("{\n  \"type\": \"FeatureCollection\",")
                writer.appendLine("  \"properties\": {")
                writer.appendLine("    \"sessionId\": ${session.id},")
                writer.appendLine("    \"sessionName\": \"${session.name}\",")
                writer.appendLine("    \"startTime\": \"${dateFormat.format(Date(session.startTime))}\",")
                writer.appendLine("    \"sampleCount\": ${samples.size},")
                writer.appendLine("    \"dominantNetwork\": \"${session.dominantNetworkType}\"")
                writer.appendLine("  },")
                writer.appendLine("  \"features\": [")

                samples.forEachIndexed { index, s ->
                    val isLast = index == samples.size - 1
                    val dateStr = dateFormat.format(Date(s.timestamp))
                    writer.appendLine("    {")
                    writer.appendLine("      \"type\": \"Feature\",")
                    writer.appendLine("      \"geometry\": {")
                    writer.appendLine("        \"type\": \"Point\",")
                    writer.appendLine("        \"coordinates\": [${s.longitude}, ${s.latitude}, ${s.altitude}]")
                    writer.appendLine("      },")
                    writer.appendLine("      \"properties\": {")
                    writer.appendLine("        \"sampleId\": ${s.id},")
                    writer.appendLine("        \"timestamp\": \"$dateStr\",")
                    writer.appendLine("        \"speedKmh\": ${s.speedKmh},")
                    writer.appendLine("        \"accuracyM\": ${s.accuracyMeters},")
                    writer.appendLine("        \"networkType\": \"${s.networkType}\",")
                    writer.appendLine("        \"operator\": \"${s.operatorName}\",")
                    writer.appendLine("        \"nrBand\": \"${s.nrBand}\",")
                    writer.appendLine("        \"nrArfcn\": ${s.nrArfcn},")
                    writer.appendLine("        \"nrPci\": ${s.nrPci},")
                    writer.appendLine("        \"nrNci\": ${s.nrNci},")
                    writer.appendLine("        \"ssRsrp\": ${s.nrSsRsrp},")
                    writer.appendLine("        \"ssRsrq\": ${s.nrSsRsrq},")
                    writer.appendLine("        \"ssSinr\": ${s.nrSsSinr},")
                    writer.appendLine("        \"lteRsrp\": ${s.lteRsrp},")
                    writer.appendLine("        \"displayRsrp\": ${s.displayRsrp},")
                    writer.appendLine("        \"displaySinr\": ${s.displaySinr},")
                    writer.appendLine("        \"gatewayIp\": \"${s.gatewayIp}\",")
                    writer.appendLine("        \"gatewayLatencyMs\": ${s.gatewayLatencyMs},")
                    writer.appendLine("        \"gatewayJitterMs\": ${s.gatewayJitterMs},")
                    writer.appendLine("        \"packetLossPct\": ${s.packetLossPct},")
                    writer.appendLine("        \"probeMethod\": \"${s.probeMethod}\",")
                    writer.appendLine("        \"iperfDlMbps\": ${s.iperfDlMbps},")
                    writer.appendLine("        \"iperfUlMbps\": ${s.iperfUlMbps}")
                    writer.appendLine("      }")
                    writer.appendLine("    }${if (isLast) "" else ","}")
                }

                writer.appendLine("  ]\n}")
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            ExportResult(true, file.absolutePath, uri)
        } catch (e: Exception) {
            ExportResult(false, "", null, e.message)
        }
    }

    fun createShareIntent(uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun getExportFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val p5gDir = File(downloadsDir, "P5G_Logs")
        if (!p5gDir.exists()) {
            p5gDir.mkdirs()
        }
        val targetDir = if (p5gDir.canWrite()) p5gDir else File(context.getExternalFilesDir(null), "P5G_Logs").also { it.mkdirs() }
        return File(targetDir, fileName)
    }
}
