package com.jpspm.travelplanner.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.data.background.IncomingShare
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.model.TripStatus
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.platform.SharedFile
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.share_no_reservations
import com.jpspm.travelplanner.resources.share_title
import com.jpspm.travelplanner.resources.share_subtitle
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.TypeBadge
import com.jpspm.travelplanner.ui.components.emoji
import com.jpspm.travelplanner.ui.components.label
import com.jpspm.travelplanner.ui.components.today
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetViewModel(
    private val incoming: IncomingShare,
    trips: TripRepository,
    private val reservations: ReservationRepository,
) : ViewModel() {

    val files: StateFlow<List<SharedFile>> = incoming.files

    /** Viagens em andamento e próximas primeiro; dentro de cada uma, reservas na ordem do roteiro. */
    val groups: StateFlow<List<Pair<Trip, List<Reservation>>>?> = trips.observeTrips().flatMapLatest { list ->
        val todayDate = today()
        val ordered = list.sortedWith(compareBy<Trip> { if (it.status(todayDate) == TripStatus.PAST) 1 else 0 }.thenBy { it.startDate })
        if (ordered.isEmpty()) flowOf(emptyList())
        else combine(ordered.map { trip -> reservations.observeReservations(trip.id) }) { arrays ->
            ordered.zip(arrays.toList()).filter { it.second.isNotEmpty() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var saving by mutableStateOf(false)
        private set

    /** Anexa todos os arquivos recebidos à reserva escolhida (RF-44). */
    fun attachTo(reservationId: String, onDone: (String) -> Unit) {
        val list = files.value
        if (list.isEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            try {
                list.forEach { f ->
                    reservations.addAttachment(reservationId, PlatformFile(f.path), if (f.isPdf) AttachmentKind.PDF else AttachmentKind.QR)
                }
                incoming.clear()
                onDone(reservationId)
            } finally {
                saving = false
            }
        }
    }

    fun cancel() = incoming.clear()
}

/** "Anexar a qual reserva?" para arquivos compartilhados de outro app (RF-44). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(onDone: (String) -> Unit, onCancel: () -> Unit, viewModel: ShareTargetViewModel = koinViewModel()) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.share_title)) },
                navigationIcon = { BackButton { viewModel.cancel(); onCancel() } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                pluralStringResource(Res.plurals.share_subtitle, files.size, files.size, files.joinToString(", ") { it.name }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
            if (viewModel.saving) LinearProgressIndicator(Modifier.padding(horizontal = 16.dp))
            val list = groups ?: return@Column
            if (list.isEmpty()) {
                Text(stringResource(Res.string.share_no_reservations), modifier = Modifier.padding(16.dp))
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                list.forEach { (trip, reservations) ->
                    item(key = "trip:${trip.id}") { SectionHeader(trip.name) }
                    items(reservations, key = { it.id }) { r ->
                        ListItem(
                            modifier = Modifier.clickable(enabled = !viewModel.saving) { viewModel.attachTo(r.id, onDone) },
                            leadingContent = { TypeBadge(r.type.emoji) },
                            headlineContent = { Text(r.title.ifBlank { stringResource(r.type.label) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { r.start?.let { Text(TimeFormat.weekdayDayMonth(it.date)) } },
                        )
                    }
                }
            }
        }
    }
}
