package com.jpspm.travelplanner.domain.planning

import com.jpspm.travelplanner.core.geo.distanceKm
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.repository.LatLng

/** Ordem sugerida para as atividades sem horário de um dia (RF-39). */
data class OrderSuggestion(
    /** IDs das atividades sem horário, na nova ordem (as sem localização vão no fim, na ordem atual). */
    val activityIds: List<String>,
    val currentKm: Double,
    val suggestedKm: Double,
) {
    val savedKm: Double get() = currentKm - suggestedKm
}

/**
 * Sugere a ordem que encurta o deslocamento entre as atividades sem horário do dia, pela heurística do
 * vizinho mais próximo, saindo da hospedagem da noite anterior (ou do primeiro lugar do dia).
 * Só sugere quando há pelo menos 3 atividades com localização e a economia vale a pena (RNF-25: nunca
 * aplica sozinho).
 */
object DayOrder {

    const val MIN_ACTIVITIES = 3
    /** Só sugere se a economia for de pelo menos 10% e 300 m. */
    private const val MIN_SAVING_RATIO = 0.10
    private const val MIN_SAVING_KM = 0.3

    fun suggest(day: AgendaDay, reservations: List<Reservation>): OrderSuggestion? {
        val untimed = day.items.filterIsInstance<AgendaItem.ActivityItem>().map { it.activity }.filter { it.startTime == null }
        val located = untimed.filter { it.place.position() != null }
        if (located.size < MIN_ACTIVITIES) return null

        val start = lodgingFor(day, reservations)?.position() ?: located.first().place.position()!!
        val currentKm = pathKm(start, located.map { it.place.position()!! })

        val remaining = located.toMutableList()
        val ordered = mutableListOf<Activity>()
        var here = start
        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { distanceKm(here, it.place.position()!!) }
            ordered += next
            remaining -= next
            here = next.place.position()!!
        }
        val suggestedKm = pathKm(start, ordered.map { it.place.position()!! })
        val saved = currentKm - suggestedKm
        if (saved < MIN_SAVING_KM || saved < currentKm * MIN_SAVING_RATIO) return null
        val unlocated = untimed.filter { it.place.position() == null }
        return OrderSuggestion((ordered + unlocated).map { it.id }, currentKm, suggestedKm)
    }

    /** Hospedagem onde se dorme na noite anterior ao dia (ou onde se faz check-in nesse dia). */
    private fun lodgingFor(day: AgendaDay, reservations: List<Reservation>): Place? =
        reservations.filter { it.type == ReservationType.LODGING }.firstOrNull { r ->
            val checkIn = r.start?.date ?: return@firstOrNull false
            val checkOut = r.end?.date ?: checkIn
            day.date in checkIn..checkOut
        }?.place

    private fun pathKm(start: LatLng, points: List<LatLng>): Double {
        var total = 0.0
        var here = start
        points.forEach {
            total += distanceKm(here, it)
            here = it
        }
        return total
    }

    private fun Place?.position(): LatLng? =
        this?.let { p -> if (p.latitude != null && p.longitude != null) LatLng(p.latitude, p.longitude) else null }
}
