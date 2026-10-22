package com.jpspm.travelplanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Datas e horas são guardadas como texto ISO-8601 (`2026-10-22`, `14:30`, `2026-10-22T14:30`)
 * junto do ID de fuso IANA. Colunas `*EpochMs` guardam o instante UTC derivado, usado só para
 * ordenar e filtrar (RNF-07). `createdAt`/`updatedAt` em epoch ms preparam a mesclagem do backup e o sync.
 * Caminhos de arquivo são relativos ao diretório privado do app, para sobreviver a backup/restauração.
 */

@Serializable
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val coverPhotoPath: String?,
    val notes: String,
    val budgetTotalCents: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val countryCode: String?,
    // Adicionada na versão 2 do banco (migração automática 1 → 2).
    @ColumnInfo(defaultValue = "NULL") val city: String? = null,
    val timeZoneId: String?,
    val osmId: String?,
    val photoPath: String?,
    val photoAuthor: String?,
    val photoLicense: String?,
    val photoSourceUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "destinations",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tripId"), Index("placeId")],
)
data class DestinationEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val country: String,
    val placeId: String?,
    val arrivalDate: String,
    val departureDate: String,
    val timeZoneId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tripId"), Index("placeId"), Index("tripId", "date")],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val date: String,
    val startTime: String?,
    val endTime: String?,
    val timeZoneId: String,
    val startEpochMs: Long?,
    val title: String,
    val notes: String,
    val placeId: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "reservations",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tripId"), Index("placeId"), Index("tripId", "startEpochMs")],
)
data class ReservationEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String,
    val title: String,
    val placeId: String?,
    val startDateTime: String?,
    val startTimeZoneId: String?,
    val startEpochMs: Long?,
    val endDateTime: String?,
    val endTimeZoneId: String?,
    val endEpochMs: Long?,
    val confirmationCode: String,
    val priceCents: Long?,
    val notes: String,
    val detailsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = ReservationEntity::class, parentColumns = ["id"], childColumns = ["reservationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("reservationId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val reservationId: String,
    val kind: String,
    val fileName: String,
    val relativePath: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Imagem estática do mapa (RF-15), da viagem inteira (`scope = TRIP`) ou de um dia (`scope = DAY`).
 * [signature] resume os pinos usados; quando os lugares mudam, a imagem é refeita. Versão 3 do banco.
 */
@Serializable
@Entity(
    tableName = "map_snapshots",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class MapSnapshotEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val scope: String,
    val date: String?,
    val relativePath: String,
    val signature: String,
    val createdAt: Long,
    val updatedAt: Long,
)
