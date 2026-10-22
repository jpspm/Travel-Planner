package com.jpspm.travelplanner.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun KeepScreenBright() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** `geo:` abre o seletor do Android com Google Maps, Waze e outros apps de mapa instalados. */
actual fun externalMapUri(latitude: Double, longitude: Double, label: String): String =
    "geo:$latitude,$longitude?q=$latitude,$longitude(${android.net.Uri.encode(label)})"

/** PdfRenderer só permite uma página aberta por vez; o [Mutex] serializa a renderização. */
private class PdfDocument(path: String) {
    private val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    fun aspectRatio(index: Int): Float = renderer.openPage(index).use { it.width.toFloat() / it.height }

    suspend fun render(index: Int, widthPx: Int): ImageBitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }

    fun close() {
        renderer.close()
        descriptor.close()
    }
}

@Composable
actual fun PdfView(path: String, modifier: Modifier) {
    val document = remember(path) { runCatching { PdfDocument(path) }.getOrNull() } ?: return
    DisposableEffect(document) { onDispose { document.close() } }
    val ratios = remember(document) { (0 until document.pageCount).map(document::aspectRatio) }
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn {
            items(document.pageCount) { index ->
                val page by produceState<ImageBitmap?>(null, document, index, widthPx) {
                    value = document.render(index, widthPx)
                }
                val pageModifier = Modifier.fillMaxWidth().aspectRatio(ratios[index]).padding(bottom = 8.dp)
                page?.let { Image(bitmap = it, contentDescription = null, modifier = pageModifier) }
                    ?: androidx.compose.foundation.layout.Spacer(pageModifier)
            }
        }
    }
}
