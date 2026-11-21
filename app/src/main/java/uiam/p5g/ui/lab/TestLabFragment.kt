package uiam.p5g.ui.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uiam.p5g.data.preferences.SettingsManager
import uiam.p5g.databinding.FragmentTestLabBinding
import uiam.p5g.qos.GatewayDetector
import uiam.p5g.qos.GatewayPingProbe
import uiam.p5g.qos.IperfClient
import java.util.Locale

class TestLabFragment : Fragment() {

    private var _binding: FragmentTestLabBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private lateinit var gatewayDetector: GatewayDetector
    private val pingProbe = GatewayPingProbe()
    private val iperfClient = IperfClient()

    private var pingJob: Job? = null
    private var iperfJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestLabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        gatewayDetector = GatewayDetector(requireContext())

        refreshTargets()
        setupListeners()
    }

    private fun refreshTargets() {
        val targetIp = if (settingsManager.isGatewayAuto) {
            val gw = gatewayDetector.detectDefaultGateway()
            gw.gatewayIp ?: "Detecting gateway..."
        } else {
            "${settingsManager.manualGatewayIp} (Manual)"
        }
        binding.tvLabGatewayTarget.text = "Gateway Target: $targetIp"
        binding.tvLabIperfTarget.text = "iperf3 Target: ${settingsManager.iperfServerIp}:${settingsManager.iperfServerPort} (${settingsManager.iperfProtocol})"
    }

    private fun setupListeners() {
        binding.btnRunPingTest.setOnClickListener {
            runGatewayPingTest()
        }

        binding.btnRunIperfDl.setOnClickListener {
            runIperfTest(isDownload = true)
        }

        binding.btnRunIperfUl.setOnClickListener {
            runIperfTest(isDownload = false)
        }
    }

    private fun runGatewayPingTest() {
        val targetIp = if (settingsManager.isGatewayAuto) {
            val gw = gatewayDetector.detectDefaultGateway()
            gw.gatewayIp ?: settingsManager.manualGatewayIp
        } else {
            settingsManager.manualGatewayIp
        }

        if (targetIp.isNullOrBlank()) {
            Toast.makeText(requireContext(), "No gateway IP available", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRunPingTest.isEnabled = false
        binding.pbPingProgress.visibility = View.VISIBLE
        binding.tvPingResults.text = "Executing 5G Gateway ping to $targetIp..."

        pingJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = pingProbe.executeProbe(
                targetIp = targetIp,
                tcpFallbackPort = settingsManager.gatewayTcpPort,
                probeCount = 5
            )

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnRunPingTest.isEnabled = true
                binding.pbPingProgress.visibility = View.GONE

                val sb = StringBuilder()
                sb.appendLine("5G Gateway Probe Summary:")
                sb.appendLine("Target: ${result.targetIp}")
                sb.appendLine("Method: ${result.probeMethod} (Dual-Mode)")
                sb.appendLine(String.format(Locale.US, "RTT Avg: %.2f ms (Min: %.2f ms / Max: %.2f ms)", result.rttAvgMs, result.rttMinMs, result.rttMaxMs))
                sb.appendLine(String.format(Locale.US, "Jitter (RFC 3550): %.2f ms", result.jitterMs))
                sb.appendLine(String.format(Locale.US, "Packet Loss: %.1f%%", result.packetLossPct))
                if (result.rawOutput.isNotBlank()) {
                    sb.appendLine("\nRaw Probe Details:\n${result.rawOutput}")
                }
                binding.tvPingResults.text = sb.toString()
            }
        }
    }

    private fun runIperfTest(isDownload: Boolean) {
        val serverIp = settingsManager.iperfServerIp
        val serverPort = settingsManager.iperfServerPort
        val duration = settingsManager.iperfDurationSec

        binding.btnRunIperfDl.isEnabled = false
        binding.btnRunIperfUl.isEnabled = false
        binding.pbIperfProgress.visibility = View.VISIBLE
        binding.tvIperfLiveRate.visibility = View.VISIBLE
        binding.tvIperfLiveRate.text = "Connecting..."
        val mode = if (isDownload) "Download" else "Upload"
        binding.tvIperfResults.text = "Connecting to iperf3 server at $serverIp:$serverPort ($mode)..."

        iperfJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = iperfClient.runTest(
                serverIp = serverIp,
                serverPort = serverPort,
                isDownload = isDownload,
                durationSec = duration,
                onProgress = { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (_binding == null) return@launch
                        binding.tvIperfLiveRate.text = "${progress.currentMbps} Mbps"
                        binding.tvIperfResults.text = "iperf3 $mode in progress: ${progress.elapsedSec}s / ${progress.totalSec}s (${progress.status})\nRate: ${progress.currentMbps} Mbps"
                    }
                }
            )

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnRunIperfDl.isEnabled = true
                binding.btnRunIperfUl.isEnabled = true
                binding.pbIperfProgress.visibility = View.GONE

                val finalRate = if (isDownload) result.dlThroughputMbps else result.ulThroughputMbps
                binding.tvIperfLiveRate.text = "$finalRate Mbps"

                val sb = StringBuilder()
                sb.appendLine("iperf3 $mode Result:")
                sb.appendLine("Status: ${if (result.isSuccess) "SUCCESS" else "FAILED"}")
                sb.appendLine("Average Throughput: $finalRate Mbps")
                sb.appendLine(String.format(Locale.US, "Total Transferred: %.2f MB", result.totalBytesTransferred / (1024.0 * 1024.0)))
                sb.appendLine("Duration: ${result.durationSec}s")
                sb.appendLine("Message: ${result.statusMessage}")
                binding.tvIperfResults.text = sb.toString()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTargets()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pingJob?.cancel()
        iperfJob?.cancel()
        _binding = null
    }
}
