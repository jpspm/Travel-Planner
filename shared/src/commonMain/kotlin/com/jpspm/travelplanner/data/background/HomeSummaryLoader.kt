package com.jpspm.travelplanner.data.background

import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.CityGrouper
import com.jpspm.travelplanner.domain.model.TripStatus
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.today_nothing_next
import com.jpspm.travelplanner.resources.today_title
import com.jpspm.travelplanner.resources.widget_no_trip
import com.jpspm.travelplanner.resources.widget_starts_in
import com.jpspm.travelplanner.resources.widget_tickets
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

/** Textos do widget "Hoje" (RF-45), já em pt-BR. */
data class HomeSummary(
    val tripName: String,
    val subtitle: String,
    val nextTitle: String,
    val nextTime: String,
    val tickets: String,
)

/**
 * Conteúdo do widget: a viagem em andamento (dia de hoje, próximo item com horário e quantos
 * ingressos/passagens há hoje) ou, sem viagem em andamento, a próxima viagem.
 */
class HomeSummaryLoader(
    private val trips: TripRepository,
    private val destinations: DestinationRepository,
    private val activities: ActivityRepository,
    private val reservations: ReservationRepository,
    private val clock: Clock,
) {
    suspend fun load(): HomeSummary {
        val now = clock.now()
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val all = trips.observeTrips().first()
        val ongoing = all.firstOrNull { it.status(today) == TripStatus.ONGOING }
        if (ongoing == null) {
            val upcoming = all.filter { it.status(today) == TripStatus.UPCOMING }.minByOrNull { it.startDate }
                ?: return HomeSummary(getString(Res.string.widget_no_trip), "", "", "", "")
            val days = (upcoming.startDate.toEpochDays() - today.toEpochDays()).toInt()
            return HomeSummary(
                tripName = upcoming.name,
                subtitle = getPluralString(Res.plurals.widget_starts_in, days, days),
                nextTitle = TimeFormat.range(upcoming.startDate, upcoming.endDate),
                nextTime = "",
                tickets = "",
            )
        }
        val reservationList = reservations.observeReservations(ongoing.id).first()
        val day = AgendaBuilder.build(listOf(today), activities.observeActivities(ongoing.id).first(), reservationList)
            .first { it.date == today }
        val cities = CityGrouper.group(destinations.observeDestinations(ongoing.id).first(), AgendaBuilder.build(ongoing.days, emptyList(), reservationList))
        val next = AgendaBuilder.nextItem(day.items, now)
        val ticketCount = day.items.filterIsInstance<AgendaItem.ReservationItem>()
            .map { it.reservation }.filter { it.attachments.isNotEmpty() }.distinctBy { it.id }.size
        return HomeSummary(
            tripName = ongoing.name,
            subtitle = getString(Res.string.today_title, TimeFormat.weekdayDayMonth(today)),
            nextTitle = next?.let { item ->
                when (item) {
                    is AgendaItem.ActivityItem -> item.activity.title
                    is AgendaItem.ReservationItem -> item.reservation.title
                }
            } ?: getString(Res.string.today_nothing_next),
            nextTime = next?.moment?.let { TimeFormat.timeWithZone(it, CityGrouper.zoneNames(cities)[it.timeZoneId]) }.orEmpty(),
            tickets = if (ticketCount > 0) getPluralString(Res.plurals.widget_tickets, ticketCount, ticketCount) else "",
        )
    }
}
