package com.jpspm.travelplanner.domain.agenda

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Reservation
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Item exibido no roteiro de um dia: uma atividade ou um momento de uma reserva. */
sealed interface AgendaItem {
    val date: LocalDate

    /** Momento de início com fuso, ou `null` para itens sem horário. */
    val moment: ZonedLocal?

    val key: String

    data class ActivityItem(val activity: Activity) : AgendaItem {
        override val date: LocalDate get() = activity.date
        override val moment: ZonedLocal? get() = activity.start
        override val key: String get() = "a:${activity.id}"
    }

    data class ReservationItem(
        val reservation: Reservation,
        val role: Role,
        override val moment: ZonedLocal,
    ) : AgendaItem {
        override val date: LocalDate get() = moment.date
        override val key: String get() = "r:${reservation.id}:$role"

        /** START = partida/check-in/retirada; END = check-out/devolução. */
        enum class Role { START, END }
    }
}

data class AgendaDay(val date: LocalDate, val items: List<AgendaItem>)

object AgendaBuilder {

    /**
     * Monta o roteiro dia a dia (RF-05, RF-20). Inclui todos os dias da viagem, mesmo vazios,
     * e também dias fora do período que tenham itens (ex.: voo que sai na véspera).
     *
     * Ordem dentro do dia: primeiro as atividades sem horário, na ordem manual; depois os itens com
     * horário pelo instante UTC derivado, o que mantém a ordem correta entre fusos diferentes.
     */
    fun build(
        tripDays: List<LocalDate>,
        activities: List<Activity>,
        reservations: List<Reservation>,
    ): List<AgendaDay> {
        val items = buildList {
            activities.forEach { add(AgendaItem.ActivityItem(it)) }
            reservations.forEach { addAll(itemsFor(it)) }
        }
        val byDate = items.groupBy { it.date }
        val dates = (tripDays + byDate.keys).toSortedSet()
        return dates.map { date -> AgendaDay(date, sortDay(byDate[date].orEmpty())) }
    }

    fun itemsFor(reservation: Reservation): List<AgendaItem.ReservationItem> {
        val start = reservation.start ?: return emptyList()
        val result = mutableListOf(AgendaItem.ReservationItem(reservation, AgendaItem.ReservationItem.Role.START, start))
        val end = reservation.end
        if (reservation.type.endIsSeparateAgendaItem && end != null) {
            result += AgendaItem.ReservationItem(reservation, AgendaItem.ReservationItem.Role.END, end)
        }
        return result
    }

    fun sortDay(items: List<AgendaItem>): List<AgendaItem> {
        val (untimed, timed) = items.partition { it.moment == null }
        val untimedSorted = untimed.sortedBy { (it as? AgendaItem.ActivityItem)?.activity?.sortOrder ?: 0 }
        val timedSorted = timed.sortedWith(
            compareBy<AgendaItem> { it.moment!!.toInstant() }
                .thenBy { (it as? AgendaItem.ActivityItem)?.activity?.sortOrder ?: Int.MAX_VALUE }
        )
        return untimedSorted + timedSorted
    }

    /** Próximo item com horário que ainda não começou (RF-09). */
    fun nextItem(items: List<AgendaItem>, now: Instant): AgendaItem? =
        items.filter { it.moment != null }
            .sortedBy { it.moment!!.toInstant() }
            .firstOrNull { it.moment!!.toInstant() >= now }
}
