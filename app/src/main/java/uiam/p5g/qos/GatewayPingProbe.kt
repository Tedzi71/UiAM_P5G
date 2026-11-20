package uiam.p5g.qos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class GatewayPingProbe {

    data class ProbeResult(
        val targetIp: String,
        val probeMethod: String, // "ICMP" or "TCP"
        val rttAvgMs: Double,
        val rttMinMs: Double,
        val rttMaxMs: Double,
        val jitterMs: Double,
        val packetLossPct: Double,
        val rawOutput: String = ""
    )

    suspend fun executeProbe(
        targetIp: String,
        tcpFallbackPort: Int = 5201,
        probeCount: Int = 3
    ): ProbeResult = withContext(Dispatchers.IO) {
        if (targetIp.isBlank()) {
            return@withContext ProbeResult(
                targetIp = "",
                probeMethod = "NONE",
                rttAvgMs = 0.0,
                rttMinMs = 0.0,
                rttMaxMs = 0.0,
                jitterMs = 0.0,
                packetLossPct = 100.0,
                rawOutput = "No target IP"
            )
        }

        // Try ICMP first
        val icmpResult = runIcmpPing(targetIp, probeCount)
        if (icmpResult.packetLossPct < 100.0 && icmpResult.rttAvgMs > 0.0) {
            return@withContext icmpResult
        }

        // Fallback to TCP connect latency if ICMP is blocked or 100% loss
        val tcpResult = runTcpProbe(targetIp, tcpFallbackPort, probeCount)
        if (tcpResult.packetLossPct < 100.0) {
            return@withContext tcpResult
        }

        // Return the ICMP failure if both failed
        icmpResult
    }

    private fun runIcmpPing(targetIp: String, count: Int): ProbeResult {
        val outputBuilder = StringBuilder()
        val rttSamples = mutableListOf<Double>()
        var lossPct = 100.0

        try {
            val process = ProcessBuilder()
                .command("/system/bin/ping", "-c", count.toString(), "-W", "1", targetIp)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                outputBuilder.appendLine(line)
                line?.let { l ->
                    // Parse individual ping line: "64 bytes from 10.45.0.1: icmp_seq=1 ttl=64 time=12.4 ms"
                    if (l.contains("time=")) {
                        val timeStr = l.substringAfter("time=").substringBefore(" ms").trim()
                        timeStr.toDoubleOrNull()?.let { rttSamples.add(it) }
                    }
                    // Parse packet loss: "3 packets transmitted, 3 received, 0% packet loss"
                    if (l.contains("packet loss")) {
                        val lossStr = l.substringBefore("% packet loss").substringAfterLast(",").trim()
                        lossStr.toDoubleOrNull()?.let { lossPct = it }
                    }
                }
            }
            process.waitFor()

            // If summary line has min/avg/max/mdev: "rtt min/avg/max/mdev = 9.812/12.415/15.201/2.112 ms"
            val summaryLine = outputBuilder.lines().firstOrNull { it.startsWith("rtt min/avg/max") }
            var minRtt = if (rttSamples.isNotEmpty()) rttSamples.minOrNull() ?: 0.0 else 0.0
            var avgRtt = if (rttSamples.isNotEmpty()) rttSamples.average() else 0.0
            var maxRtt = if (rttSamples.isNotEmpty()) rttSamples.maxOrNull() ?: 0.0 else 0.0

            if (summaryLine != null && summaryLine.contains("=")) {
                val stats = summaryLine.substringAfter("=").trim().substringBefore(" ms").split("/")
                if (stats.size >= 3) {
                    minRtt = stats[0].toDoubleOrNull() ?: minRtt
                    avgRtt = stats[1].toDoubleOrNull() ?: avgRtt
                    maxRtt = stats[2].toDoubleOrNull() ?: maxRtt
                }
            }

            val jitter = calculateRfc3550Jitter(rttSamples)

            return ProbeResult(
                targetIp = targetIp,
                probeMethod = "ICMP",
                rttAvgMs = avgRtt,
                rttMinMs = minRtt,
                rttMaxMs = maxRtt,
                jitterMs = jitter,
                packetLossPct = lossPct,
                rawOutput = outputBuilder.toString()
            )
        } catch (e: Exception) {
            return ProbeResult(
                targetIp = targetIp,
                probeMethod = "ICMP",
                rttAvgMs = 0.0,
                rttMinMs = 0.0,
                rttMaxMs = 0.0,
                jitterMs = 0.0,
                packetLossPct = 100.0,
                rawOutput = "ICMP error: ${e.message}"
            )
        }
    }

    private fun runTcpProbe(targetIp: String, port: Int, count: Int): ProbeResult {
        val rttSamples = mutableListOf<Double>()
        var failedAttempts = 0

        for (i in 1..count) {
            val start = System.nanoTime()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(targetIp, port), 1200)
                val durationMs = (System.nanoTime() - start) / 1_000_000.0
                rttSamples.add(durationMs)
            } catch (e: Exception) {
                // Check if connection was refused: that still means host received packet and responded with RST!
                val durationMs = (System.nanoTime() - start) / 1_000_000.0
                if (e.message?.contains("refused", ignoreCase = true) == true) {
                    rttSamples.add(durationMs)
                } else {
                    failedAttempts++
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        val lossPct = if (count > 0) (failedAttempts.toDouble() / count) * 100.0 else 100.0
        val minRtt = if (rttSamples.isNotEmpty()) rttSamples.minOrNull() ?: 0.0 else 0.0
        val avgRtt = if (rttSamples.isNotEmpty()) rttSamples.average() else 0.0
        val maxRtt = if (rttSamples.isNotEmpty()) rttSamples.maxOrNull() ?: 0.0 else 0.0
        val jitter = calculateRfc3550Jitter(rttSamples)

        return ProbeResult(
            targetIp = targetIp,
            probeMethod = "TCP",
            rttAvgMs = avgRtt,
            rttMinMs = minRtt,
            rttMaxMs = maxRtt,
            jitterMs = jitter,
            packetLossPct = lossPct,
            rawOutput = "TCP Connect ($port): ${rttSamples.size}/$count success"
        )
    }

    companion object {
        fun calculateRfc3550Jitter(delays: List<Double>): Double {
            if (delays.size < 2) return 0.0
            var jitter = 0.0
            for (i in 1 until delays.size) {
                val diff = abs(delays[i] - delays[i - 1])
                jitter += (diff - jitter) / 16.0
            }
            return (jitter * 100).toInt() / 100.0 // round to 2 decimals
        }
    }
}
