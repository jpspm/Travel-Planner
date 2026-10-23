package com.jpspm.travelplanner.platform

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PdfBox-Android: lê o texto sem rede. Ordena por posição para manter rótulo e valor na mesma linha. */
class AndroidPdfTextReader(private val context: Context) : PdfTextReader {
    private val initialized by lazy { PDFBoxResourceLoader.init(context) }

    override suspend fun read(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        initialized
        runCatching {
            PDDocument.load(bytes).use { document ->
                PDFTextStripper().apply { sortByPosition = true }.getText(document)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
