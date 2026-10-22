package com.jpspm.travelplanner.domain.city

import com.jpspm.travelplanner.core.geo.distanceKm
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.repository.LatLng
import kotlinx.datetime.LocalDate

/**
 * Uma cidade da viagem (RF-02): cadastrada à mão ([destination]) ou detectada a partir dos lugares
 * buscados. Reúne os itens do roteiro que acontecem nela.
 */
data class CityGroup(
    /** `d:<id do destino>` para cidades cadastradas; `c:<nome>|<país>` para as detectadas. */
    val key: String,
    val name: String,
    val countryCode: String?,
    val destination: Destination?,
    val center: LatLng?,
    val timeZoneId: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    /** Itens do roteiro na cidade, em ordem cronológica (inclui check-out e devolução). */
    val items: List<AgendaItem>,
    val photoPath: String?,
) {
    val isManual: Boolean get() = destination != null

    val lodgings: List<Reservation>
        get() = items.filterIsInstance<AgendaItem.ReservationItem>().map { it.reservation }
            .filter { it.type == ReservationType.LODGING }.distinctBy { it.id }

    val activityCount: Int get() = items.count { it is AgendaItem.ActivityItem }

    val reservationCount: Int
        get() = items.filterIsInstance<AgendaItem.ReservationItem>().map { it.reservation.id }.distinct().size

    /** Lugares com coordenadas, para o mapa da cidade. */
    val places: List<Place>
        get() = items.mapNotNull { it.place() }.filter { it.hasPosition }.distinctBy { it.id }
}

fun AgendaItem.place(): Place? = when (this) {
    is AgendaItem.ActivityItem -> activity.place
    is AgendaItem.ReservationItem -> reservation.place
}

/**
 * Agrupa a viagem por cidade. Lugares a até [RADIUS_KM] do centro de uma cidade entram nela; senão,
 * abrem uma cidade nova com o nome vindo da busca. Itens sem localização entram na cidade cujo
 * período cobre a data, quando só uma cobre.
 */
object CityGrouper {

    const val RADIUS_KM = 40.0

    private class Builder(
        val key: String,
        var name: String,
        val countryCode: String?,
        val destination: Destination?,
        var center: LatLng?,
        var timeZoneId: String?,
    ) {
        val items = mutableListOf<AgendaItem>()
        val cityNames = mutableListOf<String>()
    }

    fun group(destinations: List<Destination>, days: List<AgendaDay>): List<CityGroup> {
        val builders = destinations.map { d ->
            Builder("d:${d.id}", d.name, null, d, d.place?.position(), d.timeZoneId)
        }.toMutableList()

        val allItems = days.flatMap { it.items }
        val (located, unlocated) = allItems.partition { it.place()?.position() != null }

        // Cidades cadastradas sem coordenadas ganham a posição de um lugar com o mesmo nome de cidade,
        // antes do agrupamento, para não depender da ordem dos itens.
        for (b in builders.filter { it.center == null }) {
            b.center = located.firstNotNullOfOrNull { item ->
                val p = item.place()!!
                p.position().takeIf { (p.city ?: cityFromAddress(p.address)).equals(b.name, ignoreCase = true) }
            }
        }

        for (item in located) {
            val place = item.place()!!
            val position = place.position()!!
            val cityName = place.city ?: cityFromAddress(place.address)
            val target = builders
                .filter { it.center != null && distanceKm(it.center!!, position) <= RADIUS_KM }
                .minByOrNull { distanceKm(it.center!!, position) }
                ?: builders.firstOrNull { it.center == null && cityName != null && it.name.equals(cityName, ignoreCase = true) }
                    ?.also { it.center = position }
                ?: Builder(
                    key = "c:${(cityName ?: place.name).lowercase()}|${place.countryCode.orEmpty()}",
                    name = cityName ?: place.name,
                    countryCode = place.countryCode,
                    destination = null,
                    center = position,
                    timeZoneId = place.timeZoneId,
                ).also { builders += it }
            target.items += item
            cityName?.let { target.cityNames += it }
        }

        // Datas: as do destino cadastrado; para cidades detectadas, do primeiro ao último item.
        fun Builder.range(): ClosedRange<LocalDate>? =
            destination?.let { it.arrivalDate..it.departureDate }
                ?: items.map { it.date }.let { dates -> if (dates.isEmpty()) null else dates.min()..dates.max() }

        val ranges = builders.associateWith { it.range() }
        for (item in unlocated) {
            val covering = builders.filter { b -> ranges[b]?.contains(item.date) == true }
            covering.singleOrNull()?.items?.add(item)
        }

        return builders.map { b ->
            val sorted = allItems.filter { it in b.items } // mantém a ordem do roteiro
            val range = ranges[b]
            CityGroup(
                key = b.key,
                // Cidade detectada: o nome mais frequente entre os lugares dela, não o do primeiro.
                name = if (b.destination != null) b.name else b.cityNames.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: b.name,
                countryCode = b.countryCode ?: b.destination?.place?.countryCode,
                destination = b.destination,
                center = b.center,
                timeZoneId = b.timeZoneId ?: b.items.firstNotNullOfOrNull { it.place()?.timeZoneId },
                startDate = range?.start,
                endDate = range?.endInclusive,
                items = sorted,
                photoPath = b.destination?.place?.photoPath
                    ?: sorted.filterIsInstance<AgendaItem.ReservationItem>()
                        .firstOrNull { it.reservation.type == ReservationType.LODGING && it.reservation.place?.photoPath != null }
                        ?.reservation?.place?.photoPath
                    ?: sorted.firstNotNullOfOrNull { it.place()?.photoPath },
            )
        }.sortedWith(compareBy<CityGroup, LocalDate?>(nullsLast()) { it.startDate }.thenBy { it.name })
    }

    /** Fuso → nome da cidade, só quando o nome não é ambíguo (dois nomes no mesmo fuso, ex.: Roma e Florença). */
    fun zoneNames(groups: List<CityGroup>): Map<String, String> =
        groups.filter { it.timeZoneId != null }
            .groupBy { it.timeZoneId!! }
            .filterValues { list -> list.map { it.name.lowercase() }.distinct().size == 1 }
            .mapValues { it.value.first().name }

    /**
     * Cidade de um endereço montado pela busca (`rua, bairro, cidade, estado, país`), para lugares
     * salvos antes de a cidade ser guardada em separado.
     */
    internal fun cityFromAddress(address: String): String? =
        address.split(", ").filter { it.isNotBlank() }.let { parts -> if (parts.size >= 3) parts[parts.size - 3] else null }

    private fun Place.position(): LatLng? =
        if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
}

/** Rótulos de fuso de uma viagem (ex.: `Europe/Rome` → `Roma`), a partir das cidades cadastradas e detectadas. */
fun tripZoneNames(
    destinations: List<Destination>,
    activities: List<Activity>,
    reservations: List<Reservation>,
): Map<String, String> =
    CityGrouper.zoneNames(
        CityGrouper.group(destinations, AgendaBuilder.build(emptyList(), activities, reservations)),
    )
