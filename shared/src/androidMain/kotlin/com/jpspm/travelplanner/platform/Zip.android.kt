package com.jpspm.travelplanner.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual suspend fun writeZip(targetPath: String, entries: List<ZipSource>) = withContext(Dispatchers.IO) {
    ZipOutputStream(FileOutputStream(targetPath).buffered()).use { zip ->
        for (entry in entries) {
            // Arquivo que sumiu do disco fica de fora, em vez de virar um arquivo vazio na restauração.
            if (entry is ZipSource.File && !File(entry.path).exists()) continue
            zip.putNextEntry(ZipEntry(entry.name))
            when (entry) {
                is ZipSource.Bytes -> zip.write(entry.bytes)
                is ZipSource.File -> File(entry.path).inputStream().use { it.copyTo(zip) }
            }
            zip.closeEntry()
        }
    }
}

actual suspend fun readZip(sourcePath: String, onEntry: suspend (name: String, bytes: ByteArray) -> Unit) {
    withContext(Dispatchers.IO) {
        ZipInputStream(FileInputStream(sourcePath).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) onEntry(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }
}
