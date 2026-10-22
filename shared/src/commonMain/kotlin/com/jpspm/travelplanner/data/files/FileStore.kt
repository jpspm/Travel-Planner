package com.jpspm.travelplanner.data.files

import com.jpspm.travelplanner.core.newId
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Arquivos no armazenamento privado do app (RNF-03). O banco guarda só o caminho relativo
 * (ex.: `attachments/<uuid>.jpg`), que continua válido depois de restaurar um backup.
 */
class FileStore(private val root: () -> PlatformFile = { FileKit.filesDir }) {

    fun absolutePath(relativePath: String): String = (root() / relativePath).path

    /** Copia [source] sem alterar o conteúdo (anexos: QR codes precisam da qualidade original, RNF-10). */
    suspend fun import(source: PlatformFile, folder: Folder): String = withContext(Dispatchers.IO) {
        val ext = source.extension.lowercase().ifBlank { "bin" }
        val relative = "${folder.dirName}/${newId()}.$ext"
        val target = prepare(relative)
        source copyTo target
        relative
    }

    /** Importa uma foto redimensionada para o lado maior de [maxSide] px em JPEG ~80% (RNF-10). */
    suspend fun importPhoto(source: PlatformFile, folder: Folder, maxSide: Int = 1280): String =
        withContext(Dispatchers.IO) {
            val relative = "${folder.dirName}/${newId()}.jpg"
            val compressed = FileKit.compressImage(
                bytes = source.readBytes(),
                imageFormat = ImageFormat.JPEG,
                quality = 80,
                maxWidth = maxSide,
                maxHeight = maxSide,
            )
            prepare(relative) write compressed
            relative
        }

    /** Guarda uma foto baixada, normalizada para JPEG de até [maxSide] px (RNF-10). */
    suspend fun importPhotoBytes(bytes: ByteArray, folder: Folder, maxSide: Int = 1280): String =
        withContext(Dispatchers.IO) {
            val relative = "${folder.dirName}/${newId()}.jpg"
            val compressed = FileKit.compressImage(
                bytes = bytes,
                imageFormat = ImageFormat.JPEG,
                quality = 80,
                maxWidth = maxSide,
                maxHeight = maxSide,
            )
            prepare(relative) write compressed
            relative
        }

    /** Guarda bytes já prontos (ex.: imagem do mapa), sem recomprimir. */
    suspend fun importBytes(bytes: ByteArray, folder: Folder, extension: String): String = withContext(Dispatchers.IO) {
        val relative = "${folder.dirName}/${newId()}.$extension"
        prepare(relative) write bytes
        relative
    }

    /** Duplica um arquivo já guardado (usado ao duplicar uma viagem). */
    suspend fun duplicate(relativePath: String): String = withContext(Dispatchers.IO) {
        val folder = relativePath.substringBefore('/')
        val ext = relativePath.substringAfterLast('.', "bin")
        val relative = "$folder/${newId()}.$ext"
        val source = root() / relativePath
        if (source.exists()) source copyTo prepare(relative)
        relative
    }

    /** Grava um arquivo vindo do backup. Nomes são UUIDs: se já existe, é o mesmo arquivo. */
    suspend fun writeIfAbsent(relativePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = root() / relativePath
        if (!target.exists()) prepare(relativePath) write bytes
    }

    /** Tamanho total em bytes dos arquivos indicados (RNF-10). */
    suspend fun sizeOf(relativePaths: Collection<String>): Long = withContext(Dispatchers.IO) {
        relativePaths.sumOf { path -> (root() / path).takeIf { it.exists() }?.size() ?: 0L }
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        runCatching { (root() / relativePath).delete(mustExist = false) }
        Unit
    }

    private fun prepare(relativePath: String): PlatformFile {
        val dir = root() / relativePath.substringBefore('/')
        dir.createDirectories()
        return root() / relativePath
    }

    enum class Folder(val dirName: String) {
        ATTACHMENTS("attachments"),
        PHOTOS("photos"),
        MAPS("maps"),
    }
}
