package com.pocketcraft.server.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {
    private fun activeCapabilities(context: Context): NetworkCapabilities? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getNetworkCapabilities(network)
    }

    fun isOnline(context: Context): Boolean {
        val activeNetwork = activeCapabilities(context) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
        }
    }

    fun isCellular(context: Context): Boolean {
        val activeNetwork = activeCapabilities(context) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
