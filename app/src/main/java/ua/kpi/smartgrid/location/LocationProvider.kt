package ua.kpi.smartgrid.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Геолокація через FusedLocationProviderClient (Google Play services):
 * сервіс сам поєднує GPS, Wi-Fi і мобільну мережу й видає найточнішу позицію.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    // Чи надав користувач дозвіл на геолокацію (точну або приблизну)
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // Чи увімкнено визначення місцезнаходження в налаштуваннях пристрою
    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /**
     * Позиція користувача як Flow. callbackFlow перетворює колбеки LocationCallback на потік:
     * поки потік збирають, позиція оновлюється кожні 5 с; після скасування (вихід з екрана)
     * awaitClose знімає підписку, щоб не витрачати заряд батареї.
     */
    @SuppressLint("MissingPermission") // дозвіл перевіряється на екрані перед збором потоку
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        // Остання відома позиція — щоб не чекати першого оновлення
        client.lastLocation.addOnSuccessListener { location -> if (location != null) trySend(location) }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
