

package com.jpspm.travelplanner.domain.repository

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * Repositórios são interfaces (D11, RNF-14): hoje só existe a implementação local (Room + arquivos);
 * uma fonte remota pode ser adicionada no futuro sem mudar a UI.
 */

data class TripInput(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String,
)

interface TripRepository {
    fun observeTrips(): Flow<List<Trip>>
    fun observeTrip(id: String): Flow<Trip?>
    suspend fun getTrip(id: String): Trip?
    suspend fun create(input: TripInput): String
    suspend fun update(id: String, input: TripInput)
    suspend fun setCoverPhoto(id: String, source: PlatformFile?)

    /** Usa como capa uma cópia da foto de um lugar da viagem; a capa continua se o lugar for removido. */
    suspend fun setCoverFromPlace(id: String, placeId: String)

    /** Espaço ocupado pelos arquivos da viagem (capa, fotos e anexos), em bytes (RNF-10). */
    suspend fun storageBytes(id: String): Long

    /** Duplica a viagem com destinos, roteiro, reservas e cópias dos arquivos. Retorna o novo ID. */
    suspend fun duplicate(id: String, newName: String): String

    /** Remove a viagem e, em cascata, tudo que pertence a ela, inclusive arquivos (RNF-04). */
    suspend fun delete(id: String)
}

data class DestinationInput(
    val name: String,
    val country: String,
    val arrivalDate: LocalDate,
    val departureDate: LocalDate,
    val timeZoneId: String,
    val place: PlaceInput = PlaceInput.None,
)

interface DestinationRepository {
    fun observeDestinations(tripId: String): Flow<List<Destination>>
    suspend fun getDestination(id: String): Destination?
    suspend fun save(tripId: String, id: String?, input: DestinationInput): String
    suspend fun delete(id: String)
}

data class ActivityInput(
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val timeZoneId: String,
    val title: String,
    val notes: String,
    val place: PlaceInput,
)

interface ActivityRepository {
    fun observeActivities(tripId: String): Flow<List<Activity>>
    suspend fun getActivity(id: String): Activity?
    suspend fun save(tripId: String, id: String?, input: ActivityInput): String

    /** Move para outro dia (RF-07). Atividades sem horário vão para o fim da lista de sem horário do dia. */
    suspend fun moveToDate(id: String, date: LocalDate)

    /** Troca a posição manual com a atividade sem horário vizinha (RF-07). [up] = para cima. */
    suspend fun moveWithinDay(id: String, up: Boolean)

    /** Grava a ordem das atividades sem horário do dia (ordem sugerida, RF-39). */
    suspend fun applyOrder(tripId: String, date: LocalDate, activityIds: List<String>)

    suspend fun delete(id: String)
}

data class ReservationInput(
    val type: ReservationType,
    val title: String,
    val place: PlaceInput,
    val start: ZonedLocal?,
    val end: ZonedLocal?,
    val confirmationCode: String,
    val priceCents: Long?,
    val notes: String,
    val details: Map<String, String>,
)

interface ReservationRepository {
    fun observeReservations(tripId: String): Flow<List<Reservation>>
    fun observeReservation(id: String): Flow<Reservation?>
    suspend fun getReservation(id: String): Reservation?
    suspend fun save(tripId: String, id: String?, input: ReservationInput): String
    suspend fun delete(id: String)

    suspend fun getAttachment(id: String): Attachment?

    /** Copia o arquivo escolhido para o armazenamento privado do app e o vincula à reserva (RF-18). */
    suspend fun addAttachment(reservationId: String, source: PlatformFile, kind: AttachmentKind): Attachment
    suspend fun deleteAttachment(id: String)
}
