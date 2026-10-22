package com.jpspm.travelplanner

import com.jpspm.travelplanner.platform.ZipSource
import com.jpspm.travelplanner.platform.readZip
import com.jpspm.travelplanner.platform.writeZip
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZipTest {

    @Test
    fun writesAndReadsBytesAndFiles() = runTest {
        val dir = createTempDirectory()
        val photo = File(dir, "photo.jpg").apply { writeBytes(ByteArray(50_000) { (it % 251).toByte() }) }
        val zip = File(dir, "backup.zip")

        writeZip(
            zip.absolutePath,
            listOf(
                ZipSource.Bytes("manifest.json", """{"schemaVersion":1}""".encodeToByteArray()),
                ZipSource.File("files/photos/photo.jpg", photo.absolutePath),
                ZipSource.File("files/photos/missing.jpg", File(dir, "missing.jpg").absolutePath),
            ),
        )

        val read = mutableMapOf<String, ByteArray>()
        readZip(zip.absolutePath) { name, bytes -> read[name] = bytes }

        assertEquals(setOf("manifest.json", "files/photos/photo.jpg"), read.keys) // arquivo sumido fica de fora
        assertEquals("""{"schemaVersion":1}""", read.getValue("manifest.json").decodeToString())
        assertContentEquals(photo.readBytes(), read.getValue("files/photos/photo.jpg"))
        dir.deleteRecursively()
    }

    private fun createTempDirectory(): File = kotlin.io.path.createTempDirectory("zip-test").toFile()
}
