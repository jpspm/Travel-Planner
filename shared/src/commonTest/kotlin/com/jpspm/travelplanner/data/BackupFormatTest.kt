package com.jpspm.travelplanner.data

import com.jpspm.travelplanner.data.backup.BACKUP_SCHEMA_VERSION
import com.jpspm.travelplanner.data.backup.BackupData
import com.jpspm.travelplanner.data.backup.BackupManifest
import com.jpspm.travelplanner.data.backup.LocalBackupRepository
import com.jpspm.travelplanner.data.local.PlaceEntity
import com.jpspm.travelplanner.data.local.TripEntity
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupFormatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun acceptsOnlyAppFolders() {
        assertEquals("attachments/abc-123.pdf", LocalBackupRepository.safeRelativePath("attachments/abc-123.pdf"))
        assertEquals("photos/x.jpg", LocalBackupRepository.safeRelativePath("photos/x.jpg"))
    }

    @Test
    fun rejectsZipSlipAndUnknownPaths() {
        assertNull(LocalBackupRepository.safeRelativePath("../../databases/travel_planner.db"))
        assertNull(LocalBackupRepository.safeRelativePath("attachments/../../x"))
        assertNull(LocalBackupRepository.safeRelativePath("/data/data/x"))
        assertNull(LocalBackupRepository.safeRelativePath("settings.json"))
        assertNull(LocalBackupRepository.safeRelativePath("attachments/sub/x.pdf"))
    }

    @Test
    fun slugForFileName() {
        assertEquals("itália-2026", LocalBackupRepository.slug("Itália 2026"))
        assertEquals("roma-florença", LocalBackupRepository.slug("  Roma / Florença!! "))
    }

    @Test
    fun dataRoundTripsAndToleratesMissingNewFields() {
        val data = BackupData(
            trips = listOf(TripEntity("t", "Itália", "2027-05-01", "2027-05-04", null, "", null, 1, 2)),
            places = listOf(
                PlaceEntity(
                    id = "p", tripId = "t", name = "Coliseu", address = "Roma", latitude = 41.89, longitude = 12.49,
                    countryCode = "IT", city = "Roma", timeZoneId = "Europe/Rome", osmId = null, photoPath = null,
                    photoAuthor = null, photoLicense = null, photoSourceUrl = null, createdAt = 1, updatedAt = 2,
                ),
            ),
        )
        assertEquals(data, json.decodeFromString<BackupData>(json.encodeToString(data)))

        // Backup feito antes de existir `city` (versão 1 do banco) ainda é lido.
        val oldPlace = """{"places":[{"id":"p","tripId":"t","name":"X","address":"","latitude":null,"longitude":null,
            "countryCode":null,"timeZoneId":null,"osmId":null,"photoPath":null,"photoAuthor":null,"photoLicense":null,
            "photoSourceUrl":null,"createdAt":1,"updatedAt":1}]}"""
        assertNull(json.decodeFromString<BackupData>(oldPlace).places.single().city)
    }

    @Test
    fun manifestCarriesSchemaVersion() {
        val manifest = BackupManifest(BACKUP_SCHEMA_VERSION, "1.0", 123, listOf("t"))
        val text = json.encodeToString(manifest)
        assertEquals(manifest, json.decodeFromString<BackupManifest>(text))
        assertEquals(true, "\"schemaVersion\":1" in text)
    }
}
