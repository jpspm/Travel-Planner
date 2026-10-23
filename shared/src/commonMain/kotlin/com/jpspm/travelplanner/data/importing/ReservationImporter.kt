package com.jpspm.travelplanner.data.importing

import com.jpspm.travelplanner.domain.importing.BookingDraft
import com.jpspm.travelplanner.domain.importing.BookingParser
import com.jpspm.travelplanner.platform.PdfTextReader
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Arquivo que vai virar anexo da reserva importada. */
data class ImportFile(val file: PlatformFile, val name: String, val isPdf: Boolean)

/** Resultado da leitura: [draft] é `null` quando o PDF não tem texto (ex.: digitalizado). */
data class ImportResult(val files: List<ImportFile>, val sourceName: String?, val draft: BookingDraft?, val read: Boolean = true)

/**
 * Importação de reservas a partir de PDF (RF-50): lê o texto no aparelho e monta um rascunho. Guarda
 * os arquivos entre a tela de origem (compartilhar ou "Importar de PDF") e o formulário da reserva.
 */
class ReservationImporter(private val reader: PdfTextReader, private val clock: Clock) {
    private var pending: ImportResult? = null

    suspend fun read(files: List<ImportFile>): ImportResult {
        val pdf = files.firstOrNull { it.isPdf }
        val draft = pdf?.let { parse(it) }
        return ImportResult(files, pdf?.name, draft)
    }

    private suspend fun parse(file: ImportFile): BookingDraft? = try {
        val text = reader.read(file.file.readBytes())
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        text?.let { BookingParser.parse(it, today, file.name) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    fun setPending(result: ImportResult) {
        pending = result
    }

    /** Arquivo escolhido dentro do app: a leitura fica para o formulário, que mostra o progresso. */
    fun prepare(file: PlatformFile) {
        val item = of(file)
        pending = ImportResult(listOf(item), item.name, null, read = false)
    }

    /** Entrega o que foi preparado uma única vez (o formulário pode ser recriado). */
    fun takePending(): ImportResult? = pending.also { pending = null }

    companion object {
        fun of(file: PlatformFile): ImportFile = ImportFile(file, file.name, file.name.endsWith(".pdf", ignoreCase = true))
    }
}
