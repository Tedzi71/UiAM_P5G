package uiam.p5g.qos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class IperfClient {

    data class IperfProgress(
        val elapsedSec: Int,
        val totalSec: Int,
        val currentMbps: Double,
        val totalBytes: Long,
        val status: String
    )

    data class IperfResult(
        val isSuccess: Boolean,
        val dlThroughputMbps: Double,
        val ulThroughputMbps: Double,
        val totalBytesTransferred: Long,
        val durationSec: Int,
        val statusMessage: String
    )

    suspend fun runTest(
        serverIp: String,
        serverPort: Int = 5201,
        isDownload: Boolean = true,
        durationSec: Int = 10,
        onProgress: ((IperfProgress) -> Unit)? = null
    ): IperfResult = withContext(Dispatchers.IO) {
        if (serverIp.isBlank()) {
            return@withContext IperfResult(
                isSuccess = false,
                dlThroughputMbps = 0.0,
                ulThroughputMbps = 0.0,
                totalBytesTransferred = 0L,
                durationSec = durationSec,
                statusMessage = "Server IP not configured"
            )
        }

        var socket: Socket? = null
        try {
            socket = Socket()
            // 3-second connection timeout
            socket.connect(InetSocketAddress(serverIp, serverPort), 3000)
            socket.tcpNoDelay = true

            val outputStream: OutputStream = socket.getOutputStream()
            val inputStream: InputStream = socket.getInputStream()

            // iperf3 cookie handshake (37 bytes: 36 ASCII UUID + null byte)
            val cookie = UUID.randomUUID().toString() + "\u0000"
            outputStream.write(cookie.toByteArray(Charsets.US_ASCII))
            outputStream.flush()

            val bufferSize = 64 * 1024 // 64 KB buffer
            val buffer = ByteArray(bufferSize)

            var totalBytes: Long = 0
            val startTime = System.currentTimeMillis()
            val endTime = startTime + (durationSec * 1000L)
            var lastIntervalTime = startTime
            var lastIntervalBytes: Long = 0

            while (System.currentTimeMillis() < endTime && !socket.isClosed) {
                if (isDownload) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    totalBytes += bytesRead
                } else {
                    outputStream.write(buffer)
                    totalBytes += bufferSize
                }

                val now = System.currentTimeMillis()
                val intervalDuration = now - lastIntervalTime
                if (intervalDuration >= 1000) {
                    val intervalBytes = totalBytes - lastIntervalBytes
                    val currentMbps = (intervalBytes * 8.0) / (intervalDuration / 1000.0) / 1_000_000.0
                    val elapsedSec = ((now - startTime) / 1000).toInt()

                    onProgress?.invoke(
                        IperfProgress(
                            elapsedSec = elapsedSec,
                            totalSec = durationSec,
                            currentMbps = (currentMbps * 100).toInt() / 100.0,
                            totalBytes = totalBytes,
                            status = "RUNNING"
                        )
                    )

                    lastIntervalTime = now
                    lastIntervalBytes = totalBytes
                }
            }

            val totalDurationSec = (System.currentTimeMillis() - startTime) / 1000.0
            val avgMbps = if (totalDurationSec > 0) {
                (totalBytes * 8.0) / totalDurationSec / 1_000_000.0
            } else 0.0

            val formattedMbps = (avgMbps * 100).toInt() / 100.0

            val result = if (isDownload) {
                IperfResult(
                    isSuccess = true,
                    dlThroughputMbps = formattedMbps,
                    ulThroughputMbps = 0.0,
                    totalBytesTransferred = totalBytes,
                    durationSec = durationSec,
                    statusMessage = "Success: $formattedMbps Mbps"
                )
            } else {
                IperfResult(
                    isSuccess = true,
                    dlThroughputMbps = 0.0,
                    ulThroughputMbps = formattedMbps,
                    totalBytesTransferred = totalBytes,
                    durationSec = durationSec,
                    statusMessage = "Success: $formattedMbps Mbps"
                )
            }

            onProgress?.invoke(
                IperfProgress(
                    elapsedSec = durationSec,
                    totalSec = durationSec,
                    currentMbps = formattedMbps,
                    totalBytes = totalBytes,
                    status = "COMPLETED"
                )
            )

            result
        } catch (e: Exception) {
            val errorMsg = when {
                e is java.net.ConnectException -> "Server at $serverIp:$serverPort unreachable (Connection Refused)"
                e is java.net.SocketTimeoutException -> "Connection to $serverIp:$serverPort timed out"
                e is java.net.UnknownHostException -> "Host $serverIp unresolved"
                else -> "iperf3 error: ${e.message}"
            }

            onProgress?.invoke(
                IperfProgress(
                    elapsedSec = 0,
                    totalSec = durationSec,
                    currentMbps = 0.0,
                    totalBytes = 0L,
                    status = "UNREACHABLE"
                )
            )

            IperfResult(
                isSuccess = false,
                dlThroughputMbps = 0.0,
                ulThroughputMbps = 0.0,
                totalBytesTransferred = 0L,
                durationSec = durationSec,
                statusMessage = errorMsg
            )
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
