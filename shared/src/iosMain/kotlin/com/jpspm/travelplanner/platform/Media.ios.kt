package com.jpspm.travelplanner.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

// Não compilado nem validado no MVP (RNF-12): não há Mac disponível.

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes ?: ByteArray(0)

/** TODO(iOS): usar NWPathMonitor. Por enquanto considera sempre conectado. */
class IosConnectivity : Connectivity {
    override val online: StateFlow<Boolean> = MutableStateFlow(true)
}
