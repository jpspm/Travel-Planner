package com.jpspm.travelplanner.data.local

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.data.files.FileStore
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val detailsJson = Json { ignoreUnknownKeys = true }

internal fun encodeDetails(details: Map<String, String>): String =
    detailsJson.encodeToString(details.filterValues { it.isNotBlank() })

internal fun decodeDetails(json: String): Map<String, String> =
    runCatching { detailsJson.decodeFromString<Map<String, String>>(json) }.getOrDefault(emptyMap())

internal fun TripEntity.toDomain(files: FileStore) = Trip(
    id = id,
    name = name,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    coverPhotoPath = coverPhotoPath?.let(files::absolutePath),
    notes = notes,
    budgetTotalCents = budgetTotalCents,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun PlaceEntity.toDomain(files: FileStore) = Place(
    id = id,
    tripId = tripId,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    countryCode = countryCode,
    city = city,
    timeZoneId = timeZoneId,
    osmId = osmId,
    photoPath = photoPath?.let(files::absolutePath),
    photoAuthor = photoAuthor,
    photoLicense = photoLicense,
    photoSourceUrl = photoSourceUrl,
)

internal fun DestinationEntity.toDomain(places: Map<String, Place>) = Destination(
    id = id,
    tripId = tripId,
    name = name,
    country = country,
    place = placeId?.let(places::get),
    arrivalDate = LocalDate.parse(arrivalDate),
    departureDate = LocalDate.parse(departureDate),
    timeZoneId = timeZoneId,
    sortOrder = sortOrder,
)

internal fun ActivityEntity.toDomain(places: Map<String, Place>) = Activity(
    id = id,
    tripId = tripId,
    date = LocalDate.parse(date),
    startTime = startTime?.let(LocalTime::parse),
    endTime = endTime?.let(LocalTime::parse),
    timeZoneId = timeZoneId,
    title = title,
    notes = notes,
    place = placeId?.let(places::get),
    sortOrder = sortOrder,
)

internal fun AttachmentEntity.toDomain(files: FileStore) = Attachment(
    id = id,
    reservationId = reservationId,
    kind = AttachmentKind.valueOf(kind),
    fileName = fileName,
    filePath = files.absolutePath(relativePath),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

internal fun ReservationEntity.toDomain(places: Map<String, Place>, attachments: List<Attachment>) = Reservation(
    id = id,
    tripId = tripId,
    type = runCatching { ReservationType.valueOf(type) }.getOrDefault(ReservationType.OTHER),
    title = title,
    place = placeId?.let(places::get),
    start = zoned(startDateTime, startTimeZoneId),
    end = zoned(endDateTime, endTimeZoneId),
    confirmationCode = confirmationCode,
    priceCents = priceCents,
    notes = notes,
    details = decodeDetails(detailsJson),
    attachments = attachments,
)

private fun zoned(dateTime: String?, zone: String?): ZonedLocal? =
    if (dateTime != null && zone != null) ZonedLocal(LocalDateTime.parse(dateTime), zone) else null
