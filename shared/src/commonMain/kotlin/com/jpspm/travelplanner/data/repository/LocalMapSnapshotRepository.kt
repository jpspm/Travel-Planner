package com.jpspm.travelplanner.data.repository

import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.data.files.FileStore
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.local.MapSnapshotEntity
import com.jpspm.travelplanner.domain.repository.MapScope
import com.jpspm.travelplanner.domain.repository.MapSnapshot
import com.jpspm.travelplanner.domain.repository.MapSnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class LocalMapSnapshotRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val clock: Clock,
) : MapSnapshotRepository {

    override fun observe(tripId: String): Flow<List<MapSnapshot>> =
        db.mapSnapshotDao().observeByTrip(tripId).map { list ->
            list.map {
                MapSnapshot(
                    id = it.id,
                    tripId = it.tripId,
                    scope = MapScope.valueOf(it.scope),
                    date = it.date?.let(LocalDate::parse),
                    filePath = files.absolutePath(it.relativePath),
                    signature = it.signature,
                )
            }
        }

    override suspend fun save(tripId: String, scope: MapScope, date: LocalDate?, signature: String, jpeg: ByteArray) {
        val now = clock.now().toEpochMilliseconds()
        val existing = db.mapSnapshotDao().getByTrip(tripId).firstOrNull { it.scope == scope.name && it.date == date?.toString() }
        val relative = files.importBytes(jpeg, FileStore.Folder.MAPS, "jpg")
        db.mapSnapshotDao().upsert(
            MapSnapshotEntity(
                id = existing?.id ?: newId(),
                tripId = tripId,
                scope = scope.name,
                date = date?.toString(),
                relativePath = relative,
                signature = signature,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        existing?.let { files.delete(it.relativePath) }
    }

    override suspend fun deleteExcept(tripId: String, keep: Set<Pair<MapScope, LocalDate?>>) {
        db.mapSnapshotDao().getByTrip(tripId)
            .filter { (MapScope.valueOf(it.scope) to it.date?.let(LocalDate::parse)) !in keep }
            .forEach {
                db.mapSnapshotDao().delete(it.id)
                files.delete(it.relativePath)
            }
    }
}
