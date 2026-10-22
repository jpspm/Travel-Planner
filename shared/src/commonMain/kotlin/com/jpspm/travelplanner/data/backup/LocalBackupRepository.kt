package com.jpspm.travelplanner.data.backup

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jpspm.travelplanner.data.files.FileStore
import com.jpspm.travelplanner.data.local.ActivityEntity
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.local.AttachmentEntity
import com.jpspm.travelplanner.data.local.DestinationEntity
import com.jpspm.travelplanner.data.local.MapSnapshotEntity
import com.jpspm.travelplanner.data.local.PlaceEntity
import com.jpspm.travelplanner.data.local.ReservationEntity
import com.jpspm.travelplanner.data.local.TripEntity
import com.jpspm.travelplanner.data.settings.SettingsStore
import com.jpspm.travelplanner.domain.repository.BackupException
import com.jpspm.travelplanner.domain.repository.BackupRepository
import com.jpspm.travelplanner.domain.repository.BackupSummary
import com.jpspm.travelplanner.domain.repository.ImportMode
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.platform.ZipSource
import com.jpspm.travelplanner.platform.readZip
import com.jpspm.travelplanner.platform.writeZip
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Versão do formato do backup (RF-35). Mudou o formato? Suba o número e migre os antigos em [migrate]. */
const val BACKUP_SCHEMA_VERSION = 1
const val APP_VERSION = "1.0"

@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val tripIds: List<String>,
)

@Serializable
data class BackupData(
    val trips: List<TripEntity> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val destinations: List<DestinationEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val reservations: List<ReservationEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    /** Imagens estáticas do mapa (desde a versão 3 do banco; backups antigos não têm). */
    val mapSnapshots: List<MapSnapshotEntity> = emptyList(),
)

class LocalBackupRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val cacheDir: () -> PlatformFile = { FileKit.cacheDir },
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun export(tripIds: List<String>?): PlatformFile {
        val tripList = tripIds?.mapNotNull { db.tripDao().get(it) } ?: db.tripDao().getAll()
        val ids = tripList.map { it.id }
        val data = BackupData(
            trips = tripList,
            places = ids.flatMap { db.placeDao().getByTrip(it) },
            destinations = ids.flatMap { db.destinationDao().getByTrip(it) },
            activities = ids.flatMap { db.activityDao().getByTrip(it) },
            reservations = ids.flatMap { db.reservationDao().getByTrip(it) },
            attachments = ids.flatMap { db.attachmentDao().getByTrip(it) },
            mapSnapshots = ids.flatMap { db.mapSnapshotDao().getByTrip(it) },
        )
        val now = clock.now()
        val manifest = BackupManifest(BACKUP_SCHEMA_VERSION, APP_VERSION, now.toEpochMilliseconds(), ids)
        val filePaths = buildList {
            data.trips.mapNotNullTo(this) { it.coverPhotoPath }
            data.places.mapNotNullTo(this) { it.photoPath }
            data.attachments.mapTo(this) { it.relativePath }
            data.mapSnapshots.mapTo(this) { it.relativePath }
        }.distinct()

        val date = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val label = tripList.singleOrNull()?.name?.let { "-" + slug(it) }.orEmpty()
        val target = cacheDir() / "backup$label-$date.zip"
        writeZip(
            target.path,
            listOf(
                ZipSource.Bytes(MANIFEST, json.encodeToString(manifest).encodeToByteArray()),
                ZipSource.Bytes(DATA, json.encodeToString(data).encodeToByteArray()),
            ) + filePaths.map { ZipSource.File("$FILES_PREFIX$it", files.absolutePath(it)) },
        )
        return target
    }

    override suspend fun inspect(source: PlatformFile): BackupSummary {
        val (manifest, data) = readContents(source, writeFiles = false)
        return BackupSummary(
            schemaVersion = manifest.schemaVersion,
            appVersion = manifest.appVersion,
            exportedAt = Instant.fromEpochMilliseconds(manifest.exportedAt),
            tripNames = data.trips.map { it.name },
        )
    }

    override suspend fun import(source: PlatformFile, mode: ImportMode): Int {
        // Valida tudo antes de apagar qualquer coisa.
        val (_, data) = readContents(source, writeFiles = false)
        if (mode == ImportMode.REPLACE) db.tripDao().getAll().forEach { trips.delete(it.id) }
        readContents(source, writeFiles = true)
        db.useWriterConnection { connection ->
            connection.immediateTransaction<Unit> {
                // Ordem das chaves estrangeiras: viagem → local → destino/atividade/reserva → anexo.
                data.trips.forEach { e -> if (newer(e.updatedAt, db.tripDao().get(e.id)?.updatedAt)) db.tripDao().upsert(e) }
                data.places.forEach { e -> if (newer(e.updatedAt, db.placeDao().get(e.id)?.updatedAt)) db.placeDao().upsert(e) }
                data.destinations.forEach { e -> if (newer(e.updatedAt, db.destinationDao().get(e.id)?.updatedAt)) db.destinationDao().upsert(e) }
                data.activities.forEach { e -> if (newer(e.updatedAt, db.activityDao().get(e.id)?.updatedAt)) db.activityDao().upsert(e) }
                data.reservations.forEach { e -> if (newer(e.updatedAt, db.reservationDao().get(e.id)?.updatedAt)) db.reservationDao().upsert(e) }
                data.attachments.forEach { e -> if (newer(e.updatedAt, db.attachmentDao().get(e.id)?.updatedAt)) db.attachmentDao().upsert(e) }
                data.mapSnapshots.forEach { e -> if (newer(e.updatedAt, db.mapSnapshotDao().get(e.id)?.updatedAt)) db.mapSnapshotDao().upsert(e) }
            }
        }
        return data.trips.size
    }

    private fun newer(incoming: Long, existing: Long?): Boolean = existing == null || incoming > existing

    /** Lê o `.zip`: manifesto e dados sempre; arquivos só quando [writeFiles] (gravados direto no armazenamento do app). */
    private suspend fun readContents(source: PlatformFile, writeFiles: Boolean): Pair<BackupManifest, BackupData> {
        // O arquivo escolhido pode ser um content:// do Android; copia para um caminho real antes de abrir.
        val local = cacheDir() / "import.zip"
        local write source
        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        try {
            readZip(local.path) { name, bytes ->
                when {
                    name == MANIFEST -> manifestBytes = bytes
                    name == DATA -> dataBytes = bytes
                    writeFiles && name.startsWith(FILES_PREFIX) -> {
                        val relative = safeRelativePath(name.removePrefix(FILES_PREFIX))
                            ?: throw BackupException("Arquivo inválido no backup: $name")
                        files.writeIfAbsent(relative, bytes)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Não foi possível ler o arquivo. Ele é um backup do Travel Planner?", e)
        }
        val manifest = manifestBytes?.let { runCatching { json.decodeFromString<BackupManifest>(it.decodeToString()) }.getOrNull() }
            ?: throw BackupException("Este arquivo não é um backup do Travel Planner (falta o manifest.json).")
        if (manifest.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw BackupException("Este backup foi feito por uma versão mais nova do app. Atualize o app para importar.")
        }
        val data = dataBytes?.let { runCatching { migrate(manifest.schemaVersion, it.decodeToString()) }.getOrNull() }
            ?: throw BackupException("Os dados do backup estão corrompidos.")
        return manifest to data
    }

    /** Converte backups de formatos antigos para o atual (RNF-21). Hoje só existe a versão 1. */
    private fun migrate(@Suppress("UNUSED_PARAMETER") fromVersion: Int, dataJson: String): BackupData =
        json.decodeFromString<BackupData>(dataJson)

    override suspend fun markExported() = settings.update { it.copy(lastBackupAt = clock.now().toEpochMilliseconds()) }

    override fun observeReminder(): Flow<Boolean> =
        combine(settings.settings, db.tripDao().observeLastChange()) { s, lastChange ->
            val now = clock.now().toEpochMilliseconds()
            val last = s.lastBackupAt
            lastChange != null &&
                (last == null || (lastChange > last && now - last > REMINDER_AFTER.inWholeMilliseconds)) &&
                (s.backupReminderSnoozedUntil ?: 0) < now
        }

    override suspend fun snoozeReminder() = settings.update {
        it.copy(backupReminderSnoozedUntil = (clock.now() + SNOOZE).toEpochMilliseconds())
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val DATA = "data.json"
        const val FILES_PREFIX = "files/"
        val REMINDER_AFTER = 14.days
        val SNOOZE = 7.days

        private val allowedPath = Regex("""^(attachments|photos|maps)/[A-Za-z0-9._-]+$""")

        /** Aceita só `pasta/arquivo` nas pastas do app; bloqueia `..` e caminhos absolutos (zip slip). */
        internal fun safeRelativePath(path: String): String? = path.takeIf { allowedPath.matches(it) && ".." !in it }

        internal fun slug(text: String): String =
            text.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
                .replace(Regex("-+"), "-").trim('-').take(40)
    }
}
