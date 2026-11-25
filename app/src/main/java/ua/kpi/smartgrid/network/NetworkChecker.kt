package ua.kpi.smartgrid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Перевірка наявності підключення до інтернету перед мережевим запитом. */
class NetworkChecker(private val context: Context) {

    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
