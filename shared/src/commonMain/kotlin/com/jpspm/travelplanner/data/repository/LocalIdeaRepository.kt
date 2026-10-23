package com.jpspm.travelplanner.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.data.files.FileStore
import com.jpspm.travelplanner.data.local.ActivityEntity
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.local.IdeaEntity
import com.jpspm.travelplanner.data.local.toDomain
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.repository.Idea
import com.jpspm.travelplanner.domain.repository.IdeaInput
import com.jpspm.travelplanner.domain.repository.IdeaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

private const val ACTIVITY = "ACTIVITY"

private fun kindOf(value: String): ReservationType? =
    if (value == ACTIVITY) null else runCatching { ReservationType.valueOf(value) }.getOrNull()

class LocalIdeaRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val places: PlaceWriter,
    private val clock: Clock,
) : IdeaRepository {

    override fun observe(tripId: String): Flow<List<Idea>> =
        combine(db.ideaDao().observeByTrip(tripId), db.placeDao().observeByTrip(tripId)) { ideas, placeList ->
            val placeMap = placeList.associate { it.id to it.toDomain(files) }
            ideas.map { Idea(it.id, it.tripId, it.title, it.notes, it.placeId?.let(placeMap::get), kindOf(it.kind)) }
        }

    override suspend fun get(id: String): Idea? {
        val entity = db.ideaDao().get(id) ?: return null
        val place = entity.placeId?.let { db.placeDao().get(it) }?.toDomain(files)
        return Idea(entity.id, entity.tripId, entity.title, entity.notes, place, kindOf(entity.kind))
    }

    override suspend fun save(tripId: String, id: String?, input: IdeaInput): String {
        val now = clock.now().toEpochMilliseconds()
        val current = id?.let { db.ideaDao().get(it) }
        val placeId = places.write(tripId, current?.placeId, input.place)
        val entity = IdeaEntity(
            id = current?.id ?: newId(),
            tripId = tripId,
            title = input.title.trim(),
            notes = input.notes,
            placeId = placeId,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            kind = input.kind?.name ?: ACTIVITY,
        )
        db.ideaDao().upsert(entity)
        if (current?.placeId != null && placeId == null) places.cleanupUnused(tripId)
        return entity.id
    }

    override suspend fun schedule(id: String, date: LocalDate, timeZoneId: String): String? {
        val idea = db.ideaDao().get(id) ?: return null
        val now = clock.now().toEpochMilliseconds()
        val activityId = newId()
        db.useWriterConnection { connection ->
            connection.immediateTransaction<Unit> {
                val order = db.activityDao().maxSortOrder(idea.tripId, date.toString()) + 1
                db.activityDao().upsert(
                    ActivityEntity(
                        id = activityId,
                        tripId = idea.tripId,
                        date = date.toString(),
                        startTime = null,
                        endTime = null,
                        timeZoneId = timeZoneId,
                        startEpochMs = null,
                        title = idea.title,
                        notes = idea.notes,
                        placeId = idea.placeId, // o mesmo lugar, com foto e coordenadas
                        sortOrder = order,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                db.ideaDao().delete(id)
            }
        }
        return activityId
    }

    override suspend fun delete(id: String) {
        val idea = db.ideaDao().get(id) ?: return
        db.ideaDao().delete(id)
        places.cleanupUnused(idea.tripId)
    }
}
