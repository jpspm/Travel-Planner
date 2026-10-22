package com.jpspm.travelplanner.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.data.files.FileStore
import com.jpspm.travelplanner.data.local.ActivityEntity
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.local.AttachmentEntity
import com.jpspm.travelplanner.data.local.DestinationEntity
import com.jpspm.travelplanner.data.local.PlaceEntity
import com.jpspm.travelplanner.data.local.ReservationEntity
import com.jpspm.travelplanner.data.local.TripEntity
import com.jpspm.travelplanner.data.local.encodeDetails
import com.jpspm.travelplanner.data.local.toDomain
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.PhotoInput
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.repository.PlaceSearch
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.repository.ActivityInput
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationInput
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.ReservationInput
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripInput
import com.jpspm.travelplanner.domain.repository.TripRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlin.time.Clock

private fun Clock.millis(): Long = now().toEpochMilliseconds()

private fun ZonedLocal?.epochMs(): Long? = this?.toInstant()?.toEpochMilliseconds()

/**
 * Cria, atualiza ou desvincula o local de uma atividade, destino ou reserva. A foto é baixada aqui,
 * ao salvar, e guardada no aparelho (RF-11); se o download falhar, o local é salvo sem foto.
 */
class PlaceWriter(
    private val db: AppDatabase,
    private val files: FileStore,
    private val search: PlaceSearch,
    private val clock: Clock,
) {
    suspend fun write(tripId: String, currentPlaceId: String?, input: PlaceInput): String? {
        if (input.isBlank) return null
        val now = clock.millis()
        val existing = currentPlaceId?.let { db.placeDao().get(it) }
        val base = existing ?: PlaceEntity(
            id = newId(), tripId = tripId, name = "", address = "",
            latitude = null, longitude = null, countryCode = null, city = null, timeZoneId = null, osmId = null,
            photoPath = null, photoAuthor = null, photoLicense = null, photoSourceUrl = null,
            createdAt = now, updatedAt = now,
        )
        val withPhoto = applyPhoto(base, input.photo)
        val entity = withPhoto.copy(
            name = input.name.trim(),
            address = input.address.trim(),
            latitude = input.latitude,
            longitude = input.longitude,
            countryCode = input.countryCode,
            city = input.city,
            timeZoneId = input.timeZoneId,
            osmId = input.osmId,
            updatedAt = now,
        )
        db.placeDao().upsert(entity)
        if (base.photoPath != null && base.photoPath != entity.photoPath) files.delete(base.photoPath)
        return entity.id
    }

    private suspend fun applyPhoto(entity: PlaceEntity, photo: PhotoInput): PlaceEntity = when (photo) {
        PhotoInput.Keep -> entity
        PhotoInput.Remove -> entity.withoutPhoto()
        is PhotoInput.Local -> entity.copy(
            photoPath = files.importPhoto(photo.file, FileStore.Folder.PHOTOS),
            photoAuthor = null, photoLicense = null, photoSourceUrl = null,
        )
        is PhotoInput.Remote ->
            if (entity.photoPath != null && entity.photoSourceUrl == photo.photo.sourceUrl) entity
            else runCatching {
                val bytes = search.download(photo.photo.url)
                entity.copy(
                    photoPath = files.importPhotoBytes(bytes, FileStore.Folder.PHOTOS),
                    photoAuthor = photo.photo.author,
                    photoLicense = photo.photo.license,
                    photoSourceUrl = photo.photo.sourceUrl,
                )
            }.getOrElse { entity.withoutPhoto() }
    }

    private fun PlaceEntity.withoutPhoto() =
        copy(photoPath = null, photoAuthor = null, photoLicense = null, photoSourceUrl = null)

    /** Remove locais da viagem que ninguém mais usa, junto com as fotos deles. */
    suspend fun cleanupUnused(tripId: String) {
        val unused = db.placeDao().getUnused(tripId)
        if (unused.isEmpty()) return
        db.placeDao().deleteUnused(tripId)
        unused.mapNotNull { it.photoPath }.forEach { files.delete(it) }
    }
}

class LocalTripRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val clock: Clock,
) : TripRepository {

    override fun observeTrips(): Flow<List<Trip>> =
        db.tripDao().observeAll().map { list -> list.map { it.toDomain(files) } }

    override fun observeTrip(id: String): Flow<Trip?> =
        db.tripDao().observe(id).map { it?.toDomain(files) }

    override suspend fun getTrip(id: String): Trip? = db.tripDao().get(id)?.toDomain(files)

    override suspend fun create(input: TripInput): String {
        val now = clock.millis()
        val entity = TripEntity(
            id = newId(),
            name = input.name.trim(),
            startDate = input.startDate.toString(),
            endDate = input.endDate.toString(),
            coverPhotoPath = null,
            notes = input.notes,
            budgetTotalCents = null,
            createdAt = now,
            updatedAt = now,
        )
        db.tripDao().upsert(entity)
        return entity.id
    }

    override suspend fun update(id: String, input: TripInput) {
        val current = db.tripDao().get(id) ?: return
        db.tripDao().upsert(
            current.copy(
                name = input.name.trim(),
                startDate = input.startDate.toString(),
                endDate = input.endDate.toString(),
                notes = input.notes,
                updatedAt = clock.millis(),
            )
        )
    }

    override suspend fun setCoverFromPlace(id: String, placeId: String) {
        val current = db.tripDao().get(id) ?: return
        val photo = db.placeDao().get(placeId)?.photoPath ?: return
        val copy = files.duplicate(photo)
        db.tripDao().upsert(current.copy(coverPhotoPath = copy, updatedAt = clock.millis()))
        current.coverPhotoPath?.let { files.delete(it) }
    }

    override suspend fun setCoverPhoto(id: String, source: PlatformFile?) {
        val current = db.tripDao().get(id) ?: return
        val newPath = source?.let { files.importPhoto(it, FileStore.Folder.PHOTOS) }
        db.tripDao().upsert(current.copy(coverPhotoPath = newPath, updatedAt = clock.millis()))
        current.coverPhotoPath?.let { files.delete(it) }
    }

    override suspend fun storageBytes(id: String): Long {
        val trip = db.tripDao().get(id) ?: return 0
        val paths = buildList {
            trip.coverPhotoPath?.let(::add)
            db.placeDao().getByTrip(id).mapNotNullTo(this) { it.photoPath }
            db.attachmentDao().getByTrip(id).mapTo(this) { it.relativePath }
            db.mapSnapshotDao().getByTrip(id).mapTo(this) { it.relativePath }
        }
        return files.sizeOf(paths.toSet())
    }

    override suspend fun duplicate(id: String, newName: String): String {
        val trip = db.tripDao().get(id) ?: error("Viagem $id não encontrada")
        val now = clock.millis()
        val newTripId = newId()
        val placeIds = mutableMapOf<String, String>()
        val copiedFiles = mutableListOf<String>()
        suspend fun copyFile(path: String?): String? = path?.let { files.duplicate(it).also(copiedFiles::add) }

        val places = db.placeDao().getByTrip(id)
        val destinations = db.destinationDao().getByTrip(id)
        val activities = db.activityDao().getByTrip(id)
        val reservations = db.reservationDao().getByTrip(id)
        val attachments = db.attachmentDao().getByTrip(id).groupBy { it.reservationId }

        // Arquivos são copiados antes da transação; se ela falhar, as cópias são apagadas.
        val newCover = copyFile(trip.coverPhotoPath)
        val newPlaces = places.map { p ->
            p.copy(id = newId().also { placeIds[p.id] = it }, tripId = newTripId, photoPath = copyFile(p.photoPath), createdAt = now, updatedAt = now)
        }
        val newReservations = mutableListOf<ReservationEntity>()
        val newAttachments = mutableListOf<AttachmentEntity>()
        for (r in reservations) {
            val newReservationId = newId()
            newReservations += r.copy(id = newReservationId, tripId = newTripId, placeId = r.placeId?.let(placeIds::get), createdAt = now, updatedAt = now)
            attachments[r.id].orEmpty().forEach { a ->
                newAttachments += a.copy(id = newId(), reservationId = newReservationId, relativePath = copyFile(a.relativePath)!!, createdAt = now, updatedAt = now)
            }
        }

        try {
            db.useWriterConnection { connection ->
                connection.immediateTransaction<Unit> {
                    db.tripDao().upsert(trip.copy(id = newTripId, name = newName.trim(), coverPhotoPath = newCover, createdAt = now, updatedAt = now))
                    newPlaces.forEach { db.placeDao().upsert(it) }
                    destinations.forEach {
                        db.destinationDao().upsert(it.copy(id = newId(), tripId = newTripId, placeId = it.placeId?.let(placeIds::get), createdAt = now, updatedAt = now))
                    }
                    db.activityDao().upsertAll(activities.map {
                        it.copy(id = newId(), tripId = newTripId, placeId = it.placeId?.let(placeIds::get), createdAt = now, updatedAt = now)
                    })
                    newReservations.forEach { db.reservationDao().upsert(it) }
                    newAttachments.forEach { db.attachmentDao().upsert(it) }
                }
            }
        } catch (e: Exception) {
            copiedFiles.forEach { files.delete(it) }
            throw e
        }
        return newTripId
    }

    override suspend fun delete(id: String) {
        val trip = db.tripDao().get(id) ?: return
        val filePaths = buildList {
            trip.coverPhotoPath?.let(::add)
            db.placeDao().getByTrip(id).mapNotNullTo(this) { it.photoPath }
            db.attachmentDao().getByTrip(id).mapTo(this) { it.relativePath }
            db.mapSnapshotDao().getByTrip(id).mapTo(this) { it.relativePath }
        }
        db.tripDao().delete(id) // cascata no banco: destinos, locais, atividades, reservas, anexos
        filePaths.forEach { files.delete(it) }
    }
}

class LocalDestinationRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val clock: Clock,
    private val places: PlaceWriter,
) : DestinationRepository {

    override fun observeDestinations(tripId: String): Flow<List<Destination>> =
        combine(db.destinationDao().observeByTrip(tripId), db.placeDao().observeByTrip(tripId)) { destinations, places ->
            val placeMap = places.associate { it.id to it.toDomain(files) }
            destinations.map { it.toDomain(placeMap) }
        }

    override suspend fun getDestination(id: String): Destination? {
        val entity = db.destinationDao().get(id) ?: return null
        val place = entity.placeId?.let { db.placeDao().get(it) }?.toDomain(files)
        return entity.toDomain(listOfNotNull(place).associateBy { it.id })
    }

    override suspend fun save(tripId: String, id: String?, input: DestinationInput): String {
        val now = clock.millis()
        val current = id?.let { db.destinationDao().get(it) }
        val placeId = places.write(tripId, current?.placeId, input.place)
        val entity = DestinationEntity(
            id = current?.id ?: newId(),
            tripId = tripId,
            name = input.name.trim(),
            country = input.country.trim(),
            placeId = placeId,
            arrivalDate = input.arrivalDate.toString(),
            departureDate = input.departureDate.toString(),
            timeZoneId = input.timeZoneId,
            sortOrder = current?.sortOrder ?: (db.destinationDao().maxSortOrder(tripId) + 1),
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
        )
        db.destinationDao().upsert(entity)
        if (current?.placeId != null && placeId == null) places.cleanupUnused(tripId)
        return entity.id
    }

    override suspend fun delete(id: String) {
        val entity = db.destinationDao().get(id) ?: return
        db.destinationDao().delete(id)
        places.cleanupUnused(entity.tripId)
    }
}

class LocalActivityRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val clock: Clock,
    private val places: PlaceWriter,
) : ActivityRepository {


    override fun observeActivities(tripId: String): Flow<List<Activity>> =
        combine(db.activityDao().observeByTrip(tripId), db.placeDao().observeByTrip(tripId)) { activities, places ->
            val placeMap = places.associate { it.id to it.toDomain(files) }
            activities.map { it.toDomain(placeMap) }
        }

    override suspend fun getActivity(id: String): Activity? {
        val entity = db.activityDao().get(id) ?: return null
        val place = entity.placeId?.let { db.placeDao().get(it) }?.toDomain(files)
        return entity.toDomain(listOfNotNull(place).associateBy { it.id })
    }

    override suspend fun save(tripId: String, id: String?, input: ActivityInput): String {
        val now = clock.millis()
        val current = id?.let { db.activityDao().get(it) }
        val date = input.date.toString()
        val sortOrder = if (current != null && current.date == date) current.sortOrder
        else db.activityDao().maxSortOrder(tripId, date) + 1
        val placeId = places.write(tripId, current?.placeId, input.place)
        val start = input.startTime?.let { ZonedLocal(input.date.atTime(it), input.timeZoneId) }
        val entity = ActivityEntity(
            id = current?.id ?: newId(),
            tripId = tripId,
            date = date,
            startTime = input.startTime?.toString(),
            endTime = input.endTime?.takeIf { input.startTime != null }?.toString(),
            timeZoneId = input.timeZoneId,
            startEpochMs = start.epochMs(),
            title = input.title.trim(),
            notes = input.notes,
            placeId = placeId,
            sortOrder = sortOrder,
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
        )
        db.activityDao().upsert(entity)
        if (current?.placeId != null && placeId == null) places.cleanupUnused(tripId)
        return entity.id
    }

    override suspend fun moveToDate(id: String, date: LocalDate) {
        val current = db.activityDao().get(id) ?: return
        if (current.date == date.toString()) return
        val start = current.startTime?.let { ZonedLocal(date.atTime(LocalTime.parse(it)), current.timeZoneId) }
        db.activityDao().upsert(
            current.copy(
                date = date.toString(),
                startEpochMs = start.epochMs(),
                sortOrder = db.activityDao().maxSortOrder(current.tripId, date.toString()) + 1,
                updatedAt = clock.millis(),
            )
        )
    }

    override suspend fun moveWithinDay(id: String, up: Boolean) {
        val current = db.activityDao().get(id) ?: return
        if (current.startTime != null) return // itens com horário seguem a ordem cronológica
        val day = db.activityDao().getUntimedOfDay(current.tripId, current.date).toMutableList()
        val index = day.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in day.indices) return
        day.add(target, day.removeAt(index))
        val now = clock.millis()
        db.activityDao().upsertAll(day.mapIndexed { i, a -> a.copy(sortOrder = i, updatedAt = if (a.sortOrder != i) now else a.updatedAt) })
    }

    override suspend fun delete(id: String) {
        val entity = db.activityDao().get(id) ?: return
        db.activityDao().delete(id)
        places.cleanupUnused(entity.tripId)
    }
}

class LocalReservationRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val clock: Clock,
    private val places: PlaceWriter,
) : ReservationRepository {


    override fun observeReservations(tripId: String): Flow<List<Reservation>> =
        combine(
            db.reservationDao().observeByTrip(tripId),
            db.placeDao().observeByTrip(tripId),
            db.attachmentDao().observeByTrip(tripId),
        ) { reservations, places, attachments ->
            val placeMap = places.associate { it.id to it.toDomain(files) }
            val attachmentMap = attachments.map { it.toDomain(files) }.groupBy { it.reservationId }
            reservations.map { it.toDomain(placeMap, attachmentMap[it.id].orEmpty()) }
        }

    override fun observeReservation(id: String): Flow<Reservation?> =
        combine(db.reservationDao().observe(id), db.attachmentDao().observeByReservation(id)) { entity, attachments ->
            entity?.let { toDomain(it, attachments) }
        }

    override suspend fun getReservation(id: String): Reservation? {
        val entity = db.reservationDao().get(id) ?: return null
        return toDomain(entity, db.attachmentDao().getByReservation(id))
    }

    private suspend fun toDomain(entity: ReservationEntity, attachments: List<AttachmentEntity>): Reservation {
        val place = entity.placeId?.let { db.placeDao().get(it) }?.toDomain(files)
        return entity.toDomain(listOfNotNull(place).associateBy { it.id }, attachments.map { it.toDomain(files) })
    }

    override suspend fun save(tripId: String, id: String?, input: ReservationInput): String {
        val now = clock.millis()
        val current = id?.let { db.reservationDao().get(it) }
        val placeId = if (input.type.hasPlace) places.write(tripId, current?.placeId, input.place) else null
        val end = input.end.takeIf { input.type.hasEnd }
        val entity = ReservationEntity(
            id = current?.id ?: newId(),
            tripId = tripId,
            type = input.type.name,
            title = input.type.buildTitle(input.title, input.details),
            placeId = placeId,
            startDateTime = input.start?.dateTime?.toString(),
            startTimeZoneId = input.start?.timeZoneId,
            startEpochMs = input.start.epochMs(),
            endDateTime = end?.dateTime?.toString(),
            endTimeZoneId = end?.timeZoneId,
            endEpochMs = end.epochMs(),
            confirmationCode = input.confirmationCode.trim(),
            priceCents = input.priceCents,
            notes = input.notes,
            detailsJson = encodeDetails(input.details.filterKeys { key -> input.type.fields.any { it.key == key } }),
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
        )
        db.reservationDao().upsert(entity)
        if (current?.placeId != null && placeId == null) places.cleanupUnused(tripId)
        return entity.id
    }

    override suspend fun delete(id: String) {
        val entity = db.reservationDao().get(id) ?: return
        val paths = db.attachmentDao().getByReservation(id).map { it.relativePath }
        db.reservationDao().delete(id)
        places.cleanupUnused(entity.tripId)
        paths.forEach { files.delete(it) }
    }

    override suspend fun getAttachment(id: String): Attachment? = db.attachmentDao().get(id)?.toDomain(files)

    override suspend fun addAttachment(reservationId: String, source: PlatformFile, kind: AttachmentKind): Attachment {
        val relative = files.import(source, FileStore.Folder.ATTACHMENTS)
        val now = clock.millis()
        val entity = AttachmentEntity(
            id = newId(),
            reservationId = reservationId,
            kind = kind.name,
            fileName = source.name,
            relativePath = relative,
            createdAt = now,
            updatedAt = now,
        )
        try {
            db.attachmentDao().upsert(entity)
        } catch (e: Exception) {
            files.delete(relative)
            throw e
        }
        return entity.toDomain(files)
    }

    override suspend fun deleteAttachment(id: String) {
        val entity = db.attachmentDao().get(id) ?: return
        db.attachmentDao().delete(id)
        files.delete(entity.relativePath)
    }
}
