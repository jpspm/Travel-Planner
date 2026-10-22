package com.jpspm.travelplanner.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.model.TripStatus
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.domain.city.tripZoneNames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Resumo do dia de uma viagem em andamento (RF-09, RF-21). */
data class TodaySummary(
    val trip: Trip,
    val date: LocalDate,
    val items: List<AgendaItem>,
    val next: AgendaItem?,
    /** Reservas de hoje com anexo (ingressos, passagens), para abrir o QR code em até 2 toques (RNF-19). */
    val tickets: List<Reservation>,
    val zoneNames: Map<String, String>,
)

data class TripsUiState(
    val loading: Boolean = true,
    val ongoing: List<Trip> = emptyList(),
    val upcoming: List<Trip> = emptyList(),
    val past: List<Trip> = emptyList(),
    val today: List<TodaySummary> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && ongoing.isEmpty() && upcoming.isEmpty() && past.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel(
    private val trips: TripRepository,
    private val destinations: DestinationRepository,
    private val activities: ActivityRepository,
    private val reservations: ReservationRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Relógio que avança a cada minuto, para atualizar "Hoje" e "Próxima atividade". */
    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(60_000)
        }
    }

    val state: StateFlow<TripsUiState> = combine(trips.observeTrips(), ticker) { list, now -> list to now }
        .flatMapLatest { (list, now) ->
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val grouped = list.groupBy { it.status(today) }
            val ongoing = grouped[TripStatus.ONGOING].orEmpty()
            val base = TripsUiState(
                loading = false,
                ongoing = ongoing,
                upcoming = grouped[TripStatus.UPCOMING].orEmpty(),
                past = grouped[TripStatus.PAST].orEmpty().sortedByDescending { it.startDate },
            )
            if (ongoing.isEmpty()) flowOf(base)
            else combine(ongoing.map { todayFor(it, today, now) }) { base.copy(today = it.toList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    private fun todayFor(trip: Trip, today: LocalDate, now: Instant): Flow<TodaySummary> = combine(
        activities.observeActivities(trip.id),
        reservations.observeReservations(trip.id),
        destinations.observeDestinations(trip.id),
    ) { activityList, reservationList, destinationList ->
        val day = AgendaBuilder.build(listOf(today), activityList, reservationList).first { it.date == today }
        TodaySummary(
            trip = trip,
            date = today,
            items = day.items,
            next = AgendaBuilder.nextItem(day.items, now),
            tickets = day.items.filterIsInstance<AgendaItem.ReservationItem>()
                .map { it.reservation }
                .filter { it.attachments.isNotEmpty() }
                .distinctBy { it.id },
            zoneNames = tripZoneNames(destinationList, activityList, reservationList),
        )
    }
}
