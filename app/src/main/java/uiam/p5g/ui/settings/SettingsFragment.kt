package uiam.p5g.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import uiam.p5g.R
import uiam.p5g.data.preferences.SettingsManager
import uiam.p5g.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private var isUpdatingThemeUi = false

    private val sampleIntervalOptions = listOf("1 second (High Accuracy)", "2 seconds (Recommended)", "5 seconds (Battery Saver)")
    private val sampleIntervalValues = listOf(1, 2, 5)

    private val throughputIntervalOptions = listOf("Manual Trigger Only (Recommended)", "Every 30 seconds", "Every 60 seconds", "Every 120 seconds")
    private val throughputIntervalValues = listOf(0, 30, 60, 120)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())

        setupSpinners()
        loadCurrentSettings()
        setupListeners()
    }

    private fun setupSpinners() {
        val sampleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sampleIntervalOptions)
        binding.spinnerSampleInterval.adapter = sampleAdapter

        val throughputAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, throughputIntervalOptions)
        binding.spinnerThroughputInterval.adapter = throughputAdapter
    }

    private fun loadCurrentSettings() {
        // App Theme UI state
        updateThemeUi()

        binding.switchAutoGateway.isChecked = settingsManager.isGatewayAuto
        binding.tilManualGateway.visibility = if (settingsManager.isGatewayAuto) View.GONE else View.VISIBLE
        binding.etManualGatewayIp.setText(settingsManager.manualGatewayIp)
        binding.etGatewayTcpPort.setText(settingsManager.gatewayTcpPort.toString())

        binding.etIperfIp.setText(settingsManager.iperfServerIp)
        binding.etIperfPort.setText(settingsManager.iperfServerPort.toString())
        binding.etIperfDuration.setText(settingsManager.iperfDurationSec.toString())

        val sampleIdx = sampleIntervalValues.indexOf(settingsManager.sampleIntervalSec).coerceAtLeast(0)
        binding.spinnerSampleInterval.setSelection(sampleIdx)

        val tpIdx = throughputIntervalValues.indexOf(settingsManager.throughputAutoIntervalSec).coerceAtLeast(0)
        binding.spinnerThroughputInterval.setSelection(tpIdx)
    }

    private fun updateThemeUi() {
        isUpdatingThemeUi = true
        if (settingsManager.isDarkMode) {
            binding.toggleThemeGroup.check(R.id.btnThemeDark)
            binding.btnToggleTheme.text = "Switch to Light Theme"
        } else {
            binding.toggleThemeGroup.check(R.id.btnThemeBright)
            binding.btnToggleTheme.text = "Switch to Dark Theme"
        }
        isUpdatingThemeUi = false
    }

    private fun setupListeners() {
        // Theme selection via toggle group
        binding.toggleThemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isUpdatingThemeUi || !isChecked) return@addOnButtonCheckedListener
            val targetDarkMode = (checkedId == R.id.btnThemeDark)
            if (targetDarkMode != settingsManager.isDarkMode) {
                switchTheme(targetDarkMode)
            }
        }

        // Theme toggle button
        binding.btnToggleTheme.setOnClickListener {
            switchTheme(!settingsManager.isDarkMode)
        }

        binding.switchAutoGateway.setOnCheckedChangeListener { _, isChecked ->
            binding.tilManualGateway.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun switchTheme(darkMode: Boolean) {
        settingsManager.themeMode = if (darkMode) SettingsManager.THEME_DARK else SettingsManager.THEME_BRIGHT
        settingsManager.applyTheme()
        val themeName = if (darkMode) "Dark Theme" else "Light Theme"
        Toast.makeText(requireContext(), "Applied $themeName", Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings() {
        settingsManager.isGatewayAuto = binding.switchAutoGateway.isChecked
        settingsManager.manualGatewayIp = binding.etManualGatewayIp.text?.toString()?.trim() ?: "10.45.0.1"
        settingsManager.gatewayTcpPort = binding.etGatewayTcpPort.text?.toString()?.toIntOrNull() ?: 5201

        settingsManager.iperfServerIp = binding.etIperfIp.text?.toString()?.trim() ?: "192.168.1.100"
        settingsManager.iperfServerPort = binding.etIperfPort.text?.toString()?.toIntOrNull() ?: 5201
        settingsManager.iperfDurationSec = binding.etIperfDuration.text?.toString()?.toIntOrNull() ?: 10

        val samplePos = binding.spinnerSampleInterval.selectedItemPosition
        if (samplePos in sampleIntervalValues.indices) {
            settingsManager.sampleIntervalSec = sampleIntervalValues[samplePos]
        }

        val tpPos = binding.spinnerThroughputInterval.selectedItemPosition
        if (tpPos in throughputIntervalValues.indices) {
            settingsManager.throughputAutoIntervalSec = throughputIntervalValues[tpPos]
        }

        Toast.makeText(requireContext(), "Settings saved successfully!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
