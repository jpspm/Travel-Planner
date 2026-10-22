package com.jpspm.travelplanner.domain.map

import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.MapScope
import kotlinx.datetime.LocalDate

/** Pino desenhado na imagem estática do mapa. */
data class StaticPin(val position: LatLng, val badge: String)

/** Uma imagem estática a gerar: da viagem (pinos com o número do dia) ou de um dia (pinos na ordem do roteiro). */
data class StaticMapSpec(val scope: MapScope, val date: LocalDate?, val pins: List<StaticPin>) {
    /**
     * Resume o que muda a imagem. Igual à assinatura salva = imagem atual; diferente = refazer.
     * Inclui [STYLE_VERSION] para refazer todas quando o desenho mudar.
     */
    val signature: String
        get() = STYLE_VERSION + "|" + pins.joinToString(";") {
            "${it.badge}@${it.position.latitude.round6()},${it.position.longitude.round6()}"
        }

    companion object {
        const val STYLE_VERSION = "v1"
    }
}

private fun Double.round6(): String = ((this * 1_000_000).toLong() / 1_000_000.0).toString()

object StaticMapSpecs {

    /** Imagens necessárias para o roteiro: a da viagem e uma por dia que tenha lugares com coordenadas. */
    fun build(tripStart: LocalDate, days: List<AgendaDay>): List<StaticMapSpec> {
        val perDay = days.mapNotNull { day ->
            val pins = day.items.mappedPlaces().mapIndexed { i, pos -> StaticPin(pos, "${i + 1}") }
            if (pins.isEmpty()) null else StaticMapSpec(MapScope.DAY, day.date, pins)
        }
        val tripPins = days.flatMap { day ->
            val dayNumber = (day.date.toEpochDays() - tripStart.toEpochDays()).toInt() + 1
            day.items.mappedPlaces().map { StaticPin(it, "$dayNumber") }
        }.distinctBy { it.position }
        val trip = if (tripPins.isEmpty()) emptyList() else listOf(StaticMapSpec(MapScope.TRIP, null, tripPins))
        return trip + perDay
    }

    /** Posições dos lugares do dia, sem repetir o mesmo lugar (check-in e check-out contam uma vez). */
    private fun List<AgendaItem>.mappedPlaces(): List<LatLng> = mapNotNull { item ->
        val place = item.place() ?: return@mapNotNull null
        val lat = place.latitude ?: return@mapNotNull null
        val lon = place.longitude ?: return@mapNotNull null
        place.id to LatLng(lat, lon)
    }.distinctBy { it.first }.map { it.second }
}
