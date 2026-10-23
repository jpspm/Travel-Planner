package com.jpspm.travelplanner.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

/** PDFKit do sistema (ainda não compilado: não há Mac). */
class IosPdfTextReader : PdfTextReader {
    @OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    override suspend fun read(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        return PDFDocument(data = data).string?.takeIf { it.isNotBlank() }
    }
}
