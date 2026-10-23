package com.jpspm.travelplanner.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.domain.city.CityGroup
import com.jpspm.travelplanner.domain.city.CityGrouper
import com.jpspm.travelplanner.data.settings.SettingsStore
import com.jpspm.travelplanner.domain.planning.Conflict
import com.jpspm.travelplanner.domain.planning.ConflictDetector
import com.jpspm.travelplanner.domain.planning.DayOrder
import com.jpspm.travelplanner.domain.planning.OrderSuggestion
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jpspm.travelplanner.domain.weather.DayForecast
import com.jpspm.travelplanner.domain.weather.WeatherPlanner
import com.jpspm.travelplanner.domain.weather.WeatherRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class TripDetailUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val destinations: List<Destination> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
    val days: List<AgendaDay> = emptyList(),
    /** Cidades da viagem, cadastradas ou detectadas pelos lugares (aba Cidades). */
    val cities: List<CityGroup> = emptyList(),
    val zoneNames: Map<String, String> = emptyMap(),
    /** Avisos de conflito ainda não dispensados, por dia (RF-37). */
    val conflicts: Map<LocalDate, List<Conflict>> = emptyMap(),
    /** Ordem sugerida para as atividades sem horário, por dia (RF-39). */
    val orderSuggestions: Map<LocalDate, OrderSuggestion> = emptyMap(),
    /** Título de cada item (`a:<id>` ou `r:<id>`), para escrever os avisos. */
    val itemTitles: Map<String, String> = emptyMap(),
    val today: LocalDate? = null,
)

class TripDetailViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    destinations: DestinationRepository,
    private val activities: ActivityRepository,
    reservations: ReservationRepository,
    private val settings: SettingsStore,
    clock: Clock,
    private val weather: WeatherRepository,
) : ViewModel() {

    private val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    val state: StateFlow<TripDetailUiState> = combine(
        trips.observeTrip(tripId),
        destinations.observeDestinations(tripId),
        activities.observeActivities(tripId),
        reservations.observeReservations(tripId),
        settings.settings,
    ) { trip, destinationList, activityList, reservationList, appSettings ->
        val days = if (trip == null) emptyList() else AgendaBuilder.build(trip.days, activityList, reservationList)
        val cities = CityGrouper.group(destinationList, days)
        val conflicts = if (trip == null) emptyList() else ConflictDetector.detect(trip, days, activityList, reservationList)
            .filter { "$tripId:${it.key}" !in appSettings.dismissedConflicts }
        TripDetailUiState(
            loading = false,
            trip = trip,
            destinations = destinationList,
            reservations = reservationList,
            days = days,
            cities = cities,
            zoneNames = CityGrouper.zoneNames(cities),
            conflicts = conflicts.groupBy { it.date },
            orderSuggestions = days.mapNotNull { d -> DayOrder.suggest(d, reservationList)?.let { d.date to it } }.toMap(),
            itemTitles = activityList.associate { "a:${it.id}" to it.title } + reservationList.associate { "r:${it.id}" to it.title },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    private val dayPositions = state.map { WeatherPlanner.dayPositions(it.days, it.cities) }.distinctUntilChanged()

    /** Previsão do tempo de cada dia que estiver na janela de previsão (RF-47). */
    val forecasts: StateFlow<Map<LocalDate, DayForecast>> = combine(
        dayPositions,
        weather.observe(today, today.plus(DatePeriod(days = 16))),
    ) { positions, list -> WeatherPlanner.match(positions, list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // Atualiza quando os lugares do roteiro mudam; sem internet, fica o que estiver salvo.
        viewModelScope.launch {
            dayPositions.collectLatest { positions ->
                if (positions.isNotEmpty()) weather.refresh(WeatherPlanner.requests(positions), today)
            }
        }
    }

    fun deleteTrip(onDeleted: () -> Unit) = viewModelScope.launch {
        trips.delete(tripId)
        onDeleted()
    }

    fun duplicate(newName: String, onDuplicated: (String) -> Unit) = viewModelScope.launch {
        onDuplicated(trips.duplicate(tripId, newName))
    }

    /** Espaço usado pelos arquivos da viagem, carregado sob demanda (RNF-10). */
    var storageBytes by mutableStateOf<Long?>(null)
        private set

    fun loadStorage() = viewModelScope.launch { storageBytes = trips.storageBytes(tripId) }

    fun setCoverPhoto(file: PlatformFile?) = viewModelScope.launch { trips.setCoverPhoto(tripId, file) }

    fun setCoverFromPlace(placeId: String) = viewModelScope.launch { trips.setCoverFromPlace(tripId, placeId) }

    fun moveActivity(activityId: String, up: Boolean) = viewModelScope.launch { activities.moveWithinDay(activityId, up) }

    fun moveActivityToDate(activityId: String, date: LocalDate) = viewModelScope.launch { activities.moveToDate(activityId, date) }

    fun dismissConflict(conflict: Conflict) = viewModelScope.launch {
        settings.update { it.copy(dismissedConflicts = it.dismissedConflicts + "$tripId:${conflict.key}") }
    }

    fun applyOrder(date: LocalDate, suggestion: OrderSuggestion) =
        viewModelScope.launch { activities.applyOrder(tripId, date, suggestion.activityIds) }

    fun deleteActivity(activityId: String) = viewModelScope.launch { activities.delete(activityId) }
}
