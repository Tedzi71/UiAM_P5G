package uiam.p5g.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "p5g_settings"

        const val KEY_GATEWAY_AUTO = "key_gateway_auto"
        const val KEY_MANUAL_GATEWAY_IP = "key_manual_gateway_ip"
        const val KEY_GATEWAY_TCP_PORT = "key_gateway_tcp_port"

        const val KEY_IPERF_IP = "key_iperf_ip"
        const val KEY_IPERF_PORT = "key_iperf_port"
        const val KEY_IPERF_PROTOCOL = "key_iperf_protocol"
        const val KEY_IPERF_DURATION = "key_iperf_duration"

        const val KEY_SAMPLE_INTERVAL = "key_sample_interval"
        const val KEY_THROUGHPUT_AUTO_INTERVAL = "key_throughput_auto_interval"

        const val KEY_THEME_MODE = "key_theme_mode"
        const val THEME_DARK = "dark"
        const val THEME_BRIGHT = "bright"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    val isDarkMode: Boolean
        get() = themeMode == THEME_DARK

    fun applyTheme() {
        val mode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    var isGatewayAuto: Boolean
        get() = prefs.getBoolean(KEY_GATEWAY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_GATEWAY_AUTO, value).apply()

    var manualGatewayIp: String
        get() = prefs.getString(KEY_MANUAL_GATEWAY_IP, "10.45.0.1") ?: "10.45.0.1"
        set(value) = prefs.edit().putString(KEY_MANUAL_GATEWAY_IP, value).apply()

    var gatewayTcpPort: Int
        get() = prefs.getInt(KEY_GATEWAY_TCP_PORT, 5201)
        set(value) = prefs.edit().putInt(KEY_GATEWAY_TCP_PORT, value).apply()

    var iperfServerIp: String
        get() = prefs.getString(KEY_IPERF_IP, "192.168.1.100") ?: "192.168.1.100"
        set(value) = prefs.edit().putString(KEY_IPERF_IP, value).apply()

    var iperfServerPort: Int
        get() = prefs.getInt(KEY_IPERF_PORT, 5201)
        set(value) = prefs.edit().putInt(KEY_IPERF_PORT, value).apply()

    var iperfProtocol: String
        get() = prefs.getString(KEY_IPERF_PROTOCOL, "TCP") ?: "TCP"
        set(value) = prefs.edit().putString(KEY_IPERF_PROTOCOL, value).apply()

    var iperfDurationSec: Int
        get() = prefs.getInt(KEY_IPERF_DURATION, 10)
        set(value) = prefs.edit().putInt(KEY_IPERF_DURATION, value).apply()

    var sampleIntervalSec: Int
        get() = prefs.getInt(KEY_SAMPLE_INTERVAL, 1)
        set(value) = prefs.edit().putInt(KEY_SAMPLE_INTERVAL, value).apply()

    var throughputAutoIntervalSec: Int
        get() = prefs.getInt(KEY_THROUGHPUT_AUTO_INTERVAL, 0) // 0 = Manual only
        set(value) = prefs.edit().putInt(KEY_THROUGHPUT_AUTO_INTERVAL, value).apply()
}
