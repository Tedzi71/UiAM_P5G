package uiam.p5g.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uiam.p5g.R
import uiam.p5g.data.model.RadioMetrics
import uiam.p5g.databinding.FragmentDashboardBinding
import uiam.p5g.telemetry.RadioCollector
import uiam.p5g.ui.qoe.QoeTestActivity
import uiam.p5g.ui.qos.QosSessionActivity

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var radioCollector: RadioCollector
    private var pollingJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        radioCollector = RadioCollector(requireContext())

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnStartQosSession.setOnClickListener {
            val intent = Intent(requireContext(), QosSessionActivity::class.java)
            startActivity(intent)
        }

        binding.btnStartQoeSession.setOnClickListener {
            val intent = Intent(requireContext(), QoeTestActivity::class.java)
            startActivity(intent)
        }
    }

    private fun renderRadio(radio: RadioMetrics) {
        if (_binding == null || !isAdded) return
        val ctx = context ?: return

        // Network badge & operator
        binding.tvNetworkTypeBadge.text = radio.networkType
        val badgeColor = when {
            radio.networkType.startsWith("5G_SA") -> ContextCompat.getColor(ctx, R.color.tech_5g_sa)
            radio.networkType.contains("NSA") -> ContextCompat.getColor(ctx, R.color.tech_5g_nsa)
            else -> ContextCompat.getColor(ctx, R.color.tech_lte)
        }
        binding.tvNetworkTypeBadge.background?.mutate()?.setTint(badgeColor)

        binding.tvOperatorName.text = if (radio.operatorName.isNotEmpty()) radio.operatorName else "No Cellular Carrier"
        binding.tvMccMnc.text = if (radio.mccMnc.isNotEmpty()) "PLMN: ${radio.mccMnc}" else "PLMN: ---"

        // Band & ARFCN
        val band = if (radio.primaryBand.isNotEmpty()) radio.primaryBand else "---"
        binding.tvBandLabel.text = "Band: $band"
        val arfcn = if (radio.is5G) radio.nrArfcn else radio.lteEarfcn
        binding.tvArfcnLabel.text = if (arfcn > 0) "ARFCN: $arfcn" else "ARFCN: ---"

        // RSRP with 3GPP color scale
        val rsrp = radio.primaryRsrp
        binding.tvRsrpValue.text = if (rsrp > -140) "$rsrp dBm" else "-- dBm"
        binding.tvRsrpValue.setTextColor(getRsrpColor(rsrp))

        // SINR with 3GPP color scale
        val sinr = radio.primarySinr
        binding.tvSinrValue.text = if (sinr > -23) "$sinr dB" else "-- dB"
        binding.tvSinrValue.setTextColor(getSinrColor(sinr))

        // RSRQ
        val rsrq = if (radio.is5G) radio.nrSsRsrq else radio.lteRsrq
        binding.tvRsrqValue.text = if (rsrq > -40) "$rsrq dB" else "-- dB"

        // Cell IDs
        val pci = radio.primaryPci
        binding.tvPciValue.text = if (pci >= 0) "PCI: $pci" else "PCI: ---"
        val nci = radio.nrNci
        binding.tvNciValue.text = if (nci > 0) "NCI: $nci" else "NCI: ---"
        val tac = if (radio.is5G) radio.nrTac else radio.lteTac
        binding.tvTacValue.text = if (tac >= 0) "TAC: $tac" else "TAC: ---"
    }

    private fun startRadioPolling() {
        pollingJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val radio = radioCollector.pollCurrentState()
                withContext(Dispatchers.Main) {
                    if (_binding != null && isAdded) {
                        renderRadio(radio)
                    }
                }
                delay(2000L)
            }
        }
    }

    private fun getRsrpColor(rsrp: Int): Int {
        val ctx = context ?: return 0
        return when {
            rsrp >= -80 -> ContextCompat.getColor(ctx, R.color.color_excellent)
            rsrp >= -90 -> ContextCompat.getColor(ctx, R.color.color_good)
            rsrp >= -100 -> ContextCompat.getColor(ctx, R.color.color_mid)
            rsrp >= -110 -> ContextCompat.getColor(ctx, R.color.color_poor)
            else -> ContextCompat.getColor(ctx, R.color.color_bad)
        }
    }

    private fun getSinrColor(sinr: Int): Int {
        val ctx = context ?: return 0
        return when {
            sinr >= 15 -> ContextCompat.getColor(ctx, R.color.color_excellent)
            sinr >= 5 -> ContextCompat.getColor(ctx, R.color.color_good)
            sinr >= 0 -> ContextCompat.getColor(ctx, R.color.color_mid)
            else -> ContextCompat.getColor(ctx, R.color.color_bad)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            radioCollector.startListening()
            startRadioPolling()
        } catch (e: Exception) {
            Log.w("DashboardFragment", "onResume error: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            radioCollector.stopListening()
        } catch (e: Exception) {
            Log.w("DashboardFragment", "onPause error: ${e.message}")
        }
        pollingJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
