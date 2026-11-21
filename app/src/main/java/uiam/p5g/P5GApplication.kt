package uiam.p5g

import android.app.Application
import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import uiam.p5g.data.preferences.SettingsManager
import java.io.File

class P5GApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Apply user's saved theme (Dark or Bright)
        try {
            SettingsManager.getInstance(this).applyTheme()
        } catch (e: Exception) {
            Log.e("P5G_APP", "Failed to apply theme on launch: ${e.message}", e)
        }

        // 2. Global crash handler to catch and log any unhandled crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("P5G_CRASH", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 3. Initialize osmdroid safely with app-private storage paths
        try {
            val osmdroidBasePath = File(filesDir, "osmdroid")
            val osmdroidTileCache = File(osmdroidBasePath, "tiles")
            if (!osmdroidTileCache.exists()) {
                osmdroidTileCache.mkdirs()
            }

            val osmConfig = Configuration.getInstance()
            osmConfig.osmdroidBasePath = osmdroidBasePath
            osmConfig.osmdroidTileCache = osmdroidTileCache
            osmConfig.userAgentValue = packageName
            osmConfig.load(this, getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
        } catch (e: Exception) {
            Log.e("P5G_APP", "Failed to initialize osmdroid: ${e.message}", e)
        }
    }
}
