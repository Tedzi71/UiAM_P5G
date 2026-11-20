package uiam.p5g.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class LocationProvider(private val context: Context) : LocationListener {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    var lastKnownLocation: Location? = null
        private set

    private var isTracking = false

    @SuppressLint("MissingPermission")
    fun startTracking(minTimeMs: Long = 1000L, minDistanceMeters: Float = 0f) {
        if (isTracking) return
        isTracking = true

        try {
            // Check GPS provider
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceMeters,
                    this
                )
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }

            // Also listen to Network provider for fast initial fix
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceMeters,
                    this
                )
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        lastKnownLocation = location
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
