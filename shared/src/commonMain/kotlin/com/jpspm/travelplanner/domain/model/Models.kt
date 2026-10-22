package com.jpspm.travelplanner.domain.model

import com.jpspm.travelplanner.core.time.ZonedLocal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.time.Instant

data class Trip(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val coverPhotoPath: String?,
    val notes: String,
    val budgetTotalCents: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun status(today: LocalDate): TripStatus = when {
        today < startDate -> TripStatus.UPCOMING
        today > endDate -> TripStatus.PAST
        else -> TripStatus.ONGOING
    }

    /** Todos os dias da viagem, do início ao fim. */
    val days: List<LocalDate>
        get() = generateSequence(startDate) { it.plusDays(1) }.takeWhile { it <= endDate }.toList()
}

enum class TripStatus { UPCOMING, ONGOING, PAST }

/**
 * Local de uma viagem. Pertence a uma única viagem, para que exclusão em cascata, duplicação
 * e backup por viagem não deixem órfãos. Na fase 1 só nome e endereço são digitados à mão (RNF-02);
 * coordenadas, país, fuso e foto vêm da busca de lugares na fase 2.
 */
data class Place(
    val id: String,
    val tripId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val countryCode: String?,
    val city: String?,
    val timeZoneId: String?,
    val osmId: String?,
    val photoPath: String?,
    val photoAuthor: String?,
    val photoLicense: String?,
    val photoSourceUrl: String?,
) {
    val hasPosition: Boolean get() = latitude != null && longitude != null

    /** Crédito exibido junto da foto (RF-29), ex.: `Foto: Autor · CC BY-SA 4.0 · Wikimedia Commons`. */
    val photoCredit: String?
        get() = photoAuthor?.let { author ->
            listOf(author, photoLicense.orEmpty(), "Wikimedia Commons").filter { it.isNotBlank() }.joinToString(" · ")
        }
}

/**
 * Local a gravar junto de uma atividade, destino ou reserva: vindo da busca (com coordenadas, país,
 * fuso e foto) ou digitado à mão quando não há internet (só nome e endereço, RNF-02).
 */
data class PlaceInput(
    val name: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val countryCode: String? = null,
    val city: String? = null,
    val timeZoneId: String? = null,
    val osmId: String? = null,
    val photo: PhotoInput = PhotoInput.Keep,
) {
    val isBlank: Boolean get() = name.isBlank() && address.isBlank()

    companion object {
        val None = PlaceInput("", "")

        fun of(place: Place) = PlaceInput(
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            countryCode = place.countryCode,
            city = place.city,
            timeZoneId = place.timeZoneId,
            osmId = place.osmId,
        )
    }
}

/** O que fazer com a foto do local ao salvar. */
sealed interface PhotoInput {
    /** Mantém a foto já salva (ou nenhuma). */
    data object Keep : PhotoInput

    data object Remove : PhotoInput

    /** Baixa a foto da Wikimedia e guarda no aparelho com os créditos (RF-11). */
    data class Remote(val photo: com.jpspm.travelplanner.domain.repository.RemotePhoto) : PhotoInput

    /** Foto escolhida da galeria (RF-12). */
    data class Local(val file: io.github.vinceglb.filekit.PlatformFile) : PhotoInput
}

data class Destination(
    val id: String,
    val tripId: String,
    val name: String,
    val country: String,
    val place: Place?,
    val arrivalDate: LocalDate,
    val departureDate: LocalDate,
    val timeZoneId: String,
    val sortOrder: Int,
) {
    fun covers(date: LocalDate): Boolean = date in arrivalDate..departureDate
}

/**
 * Atividade do roteiro. O horário é opcional: atividades sem horário ("tarde livre") aparecem
 * antes das que têm horário e seguem a ordem manual ([sortOrder]). As que têm horário são ordenadas
 * pelo instante UTC derivado (RNF-07).
 */
data class Activity(
    val id: String,
    val tripId: String,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val timeZoneId: String,
    val title: String,
    val notes: String,
    val place: Place?,
    val sortOrder: Int,
) {
    val start: ZonedLocal? get() = startTime?.let { ZonedLocal(date.atTime(it), timeZoneId) }
    val end: ZonedLocal? get() = endTime?.let { ZonedLocal(date.atTime(it), timeZoneId) }
}

data class Attachment(
    val id: String,
    val reservationId: String,
    val kind: AttachmentKind,
    val fileName: String,
    val filePath: String,
    val createdAt: Instant,
)

enum class AttachmentKind { QR, PHOTO, PDF }

/**
 * Reserva tipada (RF-17). Início e fim ficam em colunas próprias para consulta e ordenação;
 * os campos específicos de cada tipo ficam em [details], indexados por [ReservationField.key].
 *
 * Reservas não geram atividades: aparecem direto no roteiro (RF-20), no dia de início e, para
 * hospedagem e aluguel de carro, também no dia de fim.
 *
 * O valor pago conta no orçamento direto da reserva; gastos avulsos não apontam para reservas,
 * evitando dupla contagem.
 */
data class Reservation(
    val id: String,
    val tripId: String,
    val type: ReservationType,
    val title: String,
    val place: Place?,
    val start: ZonedLocal?,
    val end: ZonedLocal?,
    val confirmationCode: String,
    val priceCents: Long?,
    val notes: String,
    val details: Map<String, String>,
    val attachments: List<Attachment>,
) {
    fun detail(key: String): String = details[key].orEmpty()
}

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
