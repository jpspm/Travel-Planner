package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.core.time.TimeZoneSuggester
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.city.CityGrouper
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.model.PhotoInput
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.planning.WalkEstimate
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.Attraction
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.IdeaInput
import com.jpspm.travelplanner.domain.repository.IdeaRepository
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.PlaceSearch
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.nearby_add
import com.jpspm.travelplanner.resources.nearby_added
import com.jpspm.travelplanner.resources.nearby_empty
import com.jpspm.travelplanner.resources.nearby_no_reference
import com.jpspm.travelplanner.resources.nearby_offline
import com.jpspm.travelplanner.resources.nearby_source
import com.jpspm.travelplanner.resources.nearby_title
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.map.MapMarker
import com.jpspm.travelplanner.ui.map.PlacesMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Ponto de partida da busca: hospedagem, cidade ou outro lugar da viagem com coordenadas. */
data class NearbyReference(val key: String, val label: String, val position: LatLng, val city: String?)

data class NearbyUiState(
    val references: List<NearbyReference> = emptyList(),
    val selected: NearbyReference? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val results: List<Attraction> = emptyList(),
    /** Títulos já adicionados às ideias nesta tela (e os que já existiam). */
    val added: Set<String> = emptySet(),
    val adding: Set<String> = emptySet(),
)

class NearbyViewModel(
    private val tripId: String,
    private val search: PlaceSearch,
    private val ideas: IdeaRepository,
    private val trips: TripRepository,
    private val destinations: DestinationRepository,
    private val activities: ActivityRepository,
    private val reservations: ReservationRepository,
) : ViewModel() {

    var state by mutableStateOf(NearbyUiState())
        private set
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val trip = trips.getTrip(tripId) ?: return@launch
            val reservationList = reservations.observeReservations(tripId).first()
            val days = AgendaBuilder.build(trip.days, activities.observeActivities(tripId).first(), reservationList)
            val cities = CityGrouper.group(destinations.observeDestinations(tripId).first(), days)
            fun cityOf(position: LatLng) = cities.firstOrNull { c -> c.center?.let { com.jpspm.travelplanner.core.geo.distanceKm(it, position) <= CityGrouper.RADIUS_KM } == true }?.name
            val lodgings = reservationList.filter { it.type == ReservationType.LODGING }.mapNotNull { r ->
                val p = r.place?.takeIf { it.hasPosition } ?: return@mapNotNull null
                val pos = LatLng(p.latitude!!, p.longitude!!)
                NearbyReference("p:${p.id}", "🏨 ${r.title}", pos, cityOf(pos))
            }
            val cityRefs = cities.mapNotNull { c -> c.center?.let { NearbyReference("c:${c.key}", "🏙️ ${c.name}", it, c.name) } }
            val placeRefs = days.flatMap { it.items }.mapNotNull { it.place() }.filter { it.hasPosition }.distinctBy { it.id }.map { p ->
                val pos = LatLng(p.latitude!!, p.longitude!!)
                NearbyReference("p:${p.id}", "📍 ${p.name}", pos, cityOf(pos))
            }
            val references = (lodgings + cityRefs + placeRefs).distinctBy { it.key }
            val existing = ideas.observe(tripId).first().map { it.title }.toSet()
            state = state.copy(references = references, added = existing)
            references.firstOrNull()?.let(::select)
        }
    }

    fun select(reference: NearbyReference) {
        state = state.copy(selected = reference, loading = true, error = false, results = emptyList())
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                state = state.copy(results = search.nearby(reference.position), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(loading = false, error = true)
            }
        }
    }

    /** Vira ideia com foto e créditos da Commons (RF-11, RF-29) e fuso sugerido pelas coordenadas. */
    fun add(attraction: Attraction) {
        if (attraction.title in state.added || attraction.title in state.adding) return
        state = state.copy(adding = state.adding + attraction.title)
        viewModelScope.launch {
            try {
                val photo = attraction.imageFile?.let { runCatching { search.photoForFile(it) }.getOrNull() }
                ideas.save(
                    tripId, null,
                    IdeaInput(
                        title = attraction.title,
                        notes = attraction.pageUrl.orEmpty(),
                        place = PlaceInput(
                            name = attraction.title,
                            address = "",
                            latitude = attraction.position.latitude,
                            longitude = attraction.position.longitude,
                            city = state.selected?.city,
                            timeZoneId = TimeZoneSuggester.suggest(null, attraction.position.latitude, attraction.position.longitude),
                            photo = photo?.let { PhotoInput.Remote(it) } ?: PhotoInput.Keep,
                        ),
                    ),
                )
                state = state.copy(added = state.added + attraction.title)
            } finally {
                state = state.copy(adding = state.adding - attraction.title)
            }
        }
    }
}

/** Atrações por perto (RF-42): páginas da Wikipédia próximas de um lugar da viagem, para virar ideia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(tripId: String, onBack: () -> Unit, viewModel: NearbyViewModel = koinViewModel { parametersOf(tripId) }) {
    val state = viewModel.state
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.nearby_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.references.isEmpty()) {
                Text(stringResource(Res.string.nearby_no_reference), modifier = Modifier.padding(16.dp))
                return@Column
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.references, key = { it.key }) { ref ->
                    FilterChip(selected = ref == state.selected, onClick = { viewModel.select(ref) }, label = { Text(ref.label, maxLines = 1) })
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val selected = state.selected
            PlacesMap(
                markers = state.results.mapIndexed { i, a -> MapMarker(a.title, a.position, "${i + 1}", a.title) },
                focus = if (state.results.isEmpty()) selected?.position else null,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            when {
                state.error -> Text(stringResource(Res.string.nearby_offline), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                !state.loading && state.results.isEmpty() && selected != null -> Text(stringResource(Res.string.nearby_empty), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(state.results, key = { _, a -> a.title }) { index, attraction ->
                    val added = attraction.title in state.added
                    ListItem(
                        leadingContent = {
                            if (attraction.thumbnailUrl != null) {
                                AsyncImage(
                                    model = attraction.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            } else {
                                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp))
                            }
                        },
                        headlineContent = { Text("${index + 1}. ${attraction.title}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(WalkEstimate.formatDistance(attraction.distanceMeters / 1000.0)) },
                        trailingContent = {
                            OutlinedButton(onClick = { viewModel.add(attraction) }, enabled = !added && attraction.title !in state.adding) {
                                Icon(if (added) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                                Text(stringResource(if (added) Res.string.nearby_added else Res.string.nearby_add), modifier = Modifier.padding(start = 4.dp))
                            }
                        },
                    )
                }
                item(key = "source") {
                    Text(
                        stringResource(Res.string.nearby_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
