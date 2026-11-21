package uiam.p5g.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import uiam.p5g.R
import uiam.p5g.databinding.ActivityMainBinding
import uiam.p5g.ui.dashboard.DashboardFragment
import uiam.p5g.ui.history.HistoryFragment
import uiam.p5g.ui.lab.TestLabFragment
import uiam.p5g.ui.map.MapActivity
import uiam.p5g.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val dashboardFragment = DashboardFragment()
    private val historyFragment = HistoryFragment()
    private val labFragment = TestLabFragment()
    private val settingsFragment = SettingsFragment()

    companion object {
        private const val KEY_SELECTED_NAV_ID = "KEY_SELECTED_NAV_ID"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val phoneState = permissions[Manifest.permission.READ_PHONE_STATE] == true
        Log.i("MainActivity", "Permissions result - Location: $fineLocation, Phone: $phoneState")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle OS top navbar / camera punch-hole cutout and bottom system navbar / 3-button insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { view, windowInsets ->
            val statusBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(statusBars.left, statusBars.top, statusBars.right, navBars.bottom)
            windowInsets
        }

        setupBottomNavigation()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, dashboardFragment)
                .commit()
        } else {
            // Restore previously selected tab on recreate (e.g. following theme switch)
            val savedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV_ID, R.id.nav_dashboard)
            binding.bottomNavigation.selectedItemId = savedNavId
        }

        checkAndRequestPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV_ID, binding.bottomNavigation.selectedItemId)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> dashboardFragment
                R.id.nav_history -> historyFragment
                R.id.nav_lab -> labFragment
                R.id.nav_settings -> settingsFragment
                else -> dashboardFragment
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }
    }

    fun openSessionOnMap(sessionId: Long) {
        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_SESSION_ID, sessionId)
        }
        startActivity(intent)
    }
}
