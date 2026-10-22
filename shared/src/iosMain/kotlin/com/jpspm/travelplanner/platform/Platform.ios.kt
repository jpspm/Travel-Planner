package com.jpspm.travelplanner.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen

// Não compilado nem validado no MVP (RNF-12): não há Mac disponível.

@Composable
actual fun KeepScreenBright() {
    DisposableEffect(Unit) {
        val screen = UIScreen.mainScreen
        val previous = screen.brightness
        screen.brightness = 1.0
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose {
            screen.brightness = previous
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}

actual fun externalMapUri(latitude: Double, longitude: Double, label: String): String =
    "https://maps.apple.com/?ll=$latitude,$longitude&q=" +
        label.encodeToByteArray().joinToString("") { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && b >= 0) c.toString() else "%" + (b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
        }

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PdfView(path: String, modifier: Modifier) {
    UIKitView(
        factory = {
            PDFView().apply {
                autoScales = true
                document = PDFDocument(uRL = NSURL.fileURLWithPath(path))
            }
        },
        modifier = modifier,
    )
}
