package com.jpspm.travelplanner.domain.planning

import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.model.plusDays
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Problema encontrado no roteiro (RF-37). Só informa, nunca bloqueia (RNF-26). [key] é estável, para
 * o usuário poder dispensar o aviso.
 */
sealed interface Conflict {
    val date: LocalDate
    val key: String

    /** Dois itens com horários que se sobrepõem. */
    data class Overlap(override val date: LocalDate, val first: String, val second: String) : Conflict {
        override val key get() = "overlap:$first|$second"
    }

    /** Atividade marcada durante um voo ou trem. */
    data class DuringTransit(override val date: LocalDate, val activity: String, val transit: String) : Conflict {
        override val key get() = "transit:$activity|$transit"
    }

    /** Noite sem hospedagem cadastrada (e sem estar em trânsito). */
    data class NightWithoutLodging(override val date: LocalDate) : Conflict {
        override val key get() = "night:$date"
    }

    /** Atividade fora das datas da viagem. */
    data class OutsideTrip(override val date: LocalDate, val activity: String) : Conflict {
        override val key get() = "outside:$activity"
    }
}

object ConflictDetector {

    private val TRANSIT = setOf(ReservationType.FLIGHT, ReservationType.TRAIN_BUS)

    /** Intervalo de um item no tempo real. Itens sem fim valem como um instante. */
    private data class Span(val title: String, val id: String, val start: Instant, val end: Instant, val transit: Boolean)

    fun detect(trip: Trip, days: List<AgendaDay>, activities: List<Activity>, reservations: List<Reservation>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()

        // (d) atividade fora do período da viagem
        activities.filter { it.date < trip.startDate || it.date > trip.endDate }
            .forEach { conflicts += Conflict.OutsideTrip(it.date, it.id) }

        // (a) e (b) sobreposições, olhando cada dia
        val transitSpans = reservations.filter { it.type in TRANSIT }.mapNotNull { r ->
            val start = r.start ?: return@mapNotNull null
            val end = r.end ?: start
            Span(r.title, "r:${r.id}", start.toInstant(), end.toInstant(), transit = true)
        }
        for (day in days) {
            val spans = day.items.mapNotNull { span(it) }.filterNot { it.transit }
            val dayTransit = transitSpans.filter { t -> spans.any { overlaps(it, t) } }
            // atividade durante trânsito
            spans.filter { it.id.startsWith("a:") }.forEach { a ->
                dayTransit.firstOrNull { overlaps(a, it) }?.let { t -> conflicts += Conflict.DuringTransit(day.date, a.id, t.id) }
            }
            // sobreposição entre itens que não são trânsito
            for (i in spans.indices) for (j in i + 1 until spans.size) {
                val a = spans[i]
                val b = spans[j]
                if (overlaps(a, b)) conflicts += Conflict.Overlap(day.date, a.id, b.id)
            }
        }

        // (c) noites sem hospedagem: só avisa se a viagem já tem alguma hospedagem cadastrada
        val lodgings = reservations.filter { it.type == ReservationType.LODGING && it.start != null }
        if (lodgings.isNotEmpty()) {
            var night = trip.startDate
            while (night < trip.endDate) {
                val covered = lodgings.any { r ->
                    val checkIn = r.start!!.date
                    val checkOut = r.end?.date ?: checkIn.plusDays(1)
                    night >= checkIn && night < checkOut
                }
                val inTransit = reservations.filter { it.type in TRANSIT }.any { r ->
                    val s = r.start?.date
                    val e = r.end?.date
                    s != null && e != null && s <= night && e > night
                }
                if (!covered && !inTransit) conflicts += Conflict.NightWithoutLodging(night)
                night = night.plusDays(1)
            }
        }
        return conflicts.distinctBy { it.key }
    }

    private fun span(item: AgendaItem): Span? = when (item) {
        is AgendaItem.ActivityItem -> {
            val start = item.activity.start ?: return null
            val end = item.activity.end?.takeIf { it.dateTime > start.dateTime } ?: start
            Span(item.activity.title, "a:${item.activity.id}", start.toInstant(), end.toInstant(), transit = false)
        }
        is AgendaItem.ReservationItem -> {
            val r = item.reservation
            // Check-in/check-out e retirada/devolução são momentos; voo e trem viram intervalo próprio.
            val transit = r.type in TRANSIT
            Span(r.title, "r:${r.id}", item.moment.toInstant(), item.moment.toInstant(), transit)
        }
    }

    /**
     * Intervalos se sobrepõem quando um começa antes do outro terminar. Dois instantes só conflitam se
     * forem o mesmo horário; um instante no limite de um intervalo não conflita (chegar e sair).
     */
    private fun overlaps(a: Span, b: Span): Boolean {
        val aPoint = a.start == a.end
        val bPoint = b.start == b.end
        return when {
            aPoint && bPoint -> a.start == b.start
            aPoint -> a.start > b.start && a.start < b.end
            bPoint -> b.start > a.start && b.start < a.end
            else -> a.start < b.end && b.start < a.end
        }
    }
}
