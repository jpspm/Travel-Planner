package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import com.jpspm.travelplanner.resources.nearby_title
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.city.CityGrouper
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.resources.add_activity
import com.jpspm.travelplanner.ui.components.TypeBadge
import com.jpspm.travelplanner.ui.components.emoji
import com.jpspm.travelplanner.ui.components.label
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.Idea
import com.jpspm.travelplanner.domain.repository.IdeaRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_delete
import com.jpspm.travelplanner.resources.action_more
import com.jpspm.travelplanner.resources.idea_delete_message
import com.jpspm.travelplanner.resources.idea_delete_title
import com.jpspm.travelplanner.resources.idea_schedule
import com.jpspm.travelplanner.resources.ideas_empty_message
import com.jpspm.travelplanner.resources.ideas_empty_title
import com.jpspm.travelplanner.resources.ideas_no_place
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.DatePickerModal
import com.jpspm.travelplanner.ui.components.EmptyState
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.editors.defaultTimeZoneFor
import com.jpspm.travelplanner.ui.place.PlacePhoto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class IdeasUiState(val loading: Boolean = true, val ideas: List<Idea> = emptyList(), val destinations: List<Destination> = emptyList())

class IdeasViewModel(
    private val tripId: String,
    private val ideas: IdeaRepository,
    destinations: DestinationRepository,
) : ViewModel() {

    val state: StateFlow<IdeasUiState> = combine(ideas.observe(tripId), destinations.observeDestinations(tripId)) { list, dest ->
        IdeasUiState(loading = false, ideas = list, destinations = dest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdeasUiState())

    /** Vira atividade sem horário no dia escolhido; o fuso vem do lugar, senão da cidade do dia. */
    fun schedule(idea: Idea, date: LocalDate) = viewModelScope.launch {
        val zone = idea.place?.timeZoneId ?: defaultTimeZoneFor(date, state.value.destinations)
        ideas.schedule(idea.id, date, zone)
    }

    fun delete(id: String) = viewModelScope.launch { ideas.delete(id) }
}

/** Aba Ideias (RF-36): o que quero fazer, ainda sem dia, agrupado pela cidade do lugar. */
@Composable
fun IdeasTab(
    tripId: String,
    defaultDate: LocalDate,
    onOpenIdea: (String) -> Unit,
    onOpenNearby: () -> Unit,
    /** Ideia de reserva levada para o roteiro: abre o formulário da reserva já preenchido. */
    onScheduleReservation: (ideaId: String, type: ReservationType, date: LocalDate) -> Unit,
    modifier: Modifier,
    viewModel: IdeasViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var scheduling by remember { mutableStateOf<Idea?>(null) }
    var deleting by remember { mutableStateOf<Idea?>(null) }

    if (!state.loading && state.ideas.isEmpty()) {
        Column(modifier) {
            EmptyState("💡", stringResource(Res.string.ideas_empty_title), stringResource(Res.string.ideas_empty_message), Modifier.weight(1f))
            NearbyButton(onOpenNearby, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 96.dp))
        }
        return
    }
    val noPlace = stringResource(Res.string.ideas_no_place)
    val groups = state.ideas.groupBy { idea ->
        idea.place?.let { it.city ?: CityGrouper.cityFromAddress(it.address) } ?: noPlace
    }.toList().sortedBy { (city, _) -> if (city == noPlace) "￿" else city }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "nearby") { NearbyButton(onOpenNearby, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        groups.forEach { (city, ideas) ->
            item(key = "city:$city") { SectionHeader(city, Modifier) }
            items(ideas, key = { it.id }) { idea ->
                IdeaRow(
                    idea = idea,
                    onClick = { onOpenIdea(idea.id) },
                    onSchedule = { scheduling = idea },
                    onDelete = { deleting = idea },
                )
            }
        }
    }

    scheduling?.let { idea ->
        DatePickerModal(
            initial = defaultDate,
            onPick = { date ->
                val kind = idea.kind
                if (kind == null) viewModel.schedule(idea, date) else onScheduleReservation(idea.id, kind, date)
            },
            onDismiss = { scheduling = null },
        )
    }
    deleting?.let { idea ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.idea_delete_title),
            message = stringResource(Res.string.idea_delete_message, idea.title),
            onConfirm = { viewModel.delete(idea.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NearbyButton(onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Search, contentDescription = null)
        Text(stringResource(Res.string.nearby_title), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun IdeaRow(idea: Idea, onClick: () -> Unit, onSchedule: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (idea.place?.photoPath != null) PlacePhoto(idea.place.photoPath, size = 52)
            else TypeBadge(idea.kind?.emoji ?: "📍")
        },
        overlineContent = { Text(idea.kind?.let { stringResource(it.label) } ?: stringResource(Res.string.add_activity)) },
        headlineContent = { Text(idea.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val place = idea.place
            val text = listOfNotNull(
                place?.name?.takeIf { it != idea.title && it.isNotBlank() },
                place?.address?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { idea.notes }
            if (text.isNotBlank()) Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.idea_schedule)) }, onClick = { menu = false; onSchedule() })
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        },
    )
}
