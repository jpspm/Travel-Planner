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
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
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
    val today: LocalDate? = null,
)

class TripDetailViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    destinations: DestinationRepository,
    private val activities: ActivityRepository,
    reservations: ReservationRepository,
    clock: Clock,
) : ViewModel() {

    private val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    val state: StateFlow<TripDetailUiState> = combine(
        trips.observeTrip(tripId),
        destinations.observeDestinations(tripId),
        activities.observeActivities(tripId),
        reservations.observeReservations(tripId),
    ) { trip, destinationList, activityList, reservationList ->
        val days = if (trip == null) emptyList() else AgendaBuilder.build(trip.days, activityList, reservationList)
        val cities = CityGrouper.group(destinationList, days)
        TripDetailUiState(
            loading = false,
            trip = trip,
            destinations = destinationList,
            reservations = reservationList,
            days = days,
            cities = cities,
            zoneNames = CityGrouper.zoneNames(cities),
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

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

    fun deleteActivity(activityId: String) = viewModelScope.launch { activities.delete(activityId) }
}
