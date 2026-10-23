package com.jpspm.travelplanner.platform

/** Extrai o texto de um PDF no próprio aparelho, para importar reservas (RF-50). */
interface PdfTextReader {
    /** Texto de todas as páginas; `null` se o PDF não tiver texto (ex.: digitalizado) ou não abrir. */
    suspend fun read(bytes: ByteArray): String?
}
