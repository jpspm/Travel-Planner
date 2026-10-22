package com.jpspm.travelplanner.platform

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().use { out ->
        asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

class AndroidConnectivity(context: Context) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(isOnlineNow())
    override val online: StateFlow<Boolean> = state

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = isOnlineNow() }
            override fun onLost(network: Network) { state.value = isOnlineNow() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { state.value = isOnlineNow() }
        })
    }

    private fun isOnlineNow(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
