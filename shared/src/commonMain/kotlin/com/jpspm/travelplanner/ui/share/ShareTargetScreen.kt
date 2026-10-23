package com.jpspm.travelplanner.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.jpspm.travelplanner.data.importing.ImportFile
import com.jpspm.travelplanner.data.importing.ImportResult
import com.jpspm.travelplanner.data.importing.ReservationImporter
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.model.TripStatus
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.platform.SharedFile
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.import_reading
import com.jpspm.travelplanner.resources.share_attach_existing
import com.jpspm.travelplanner.resources.share_new_reservation
import com.jpspm.travelplanner.resources.share_new_reservation_hint
import com.jpspm.travelplanner.resources.share_no_reservations
import com.jpspm.travelplanner.resources.share_subtitle
import com.jpspm.travelplanner.resources.share_suggested_trip
import com.jpspm.travelplanner.resources.share_title
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
import kotlinx.coroutines.flow.map
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
    private val importer: ReservationImporter,
) : ViewModel() {

    val files: StateFlow<List<SharedFile>> = incoming.files

    /** Viagens em andamento e próximas primeiro. */
    val trips: StateFlow<List<Trip>?> = trips.observeTrips().map { list ->
        val todayDate = today()
        list.sortedWith(compareBy<Trip> { if (it.status(todayDate) == TripStatus.PAST) 1 else 0 }.thenBy { it.startDate })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Dentro de cada viagem, reservas na ordem do roteiro. */
    val groups: StateFlow<List<Pair<Trip, List<Reservation>>>?> = this.trips.flatMapLatest { ordered ->
        when {
            ordered == null -> flowOf(null)
            ordered.isEmpty() -> flowOf(emptyList())
            else -> combine(ordered.map { trip -> reservations.observeReservations(trip.id) }) { arrays ->
                ordered.zip(arrays.toList()).filter { it.second.isNotEmpty() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Leitura do PDF recebido (RF-50), feita assim que a tela abre. */
    var imported by mutableStateOf<ImportResult?>(null)
        private set
    var reading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        val list = files.value
        if (list.any { it.isPdf }) {
            reading = true
            viewModelScope.launch {
                try {
                    imported = importer.read(list.map { ImportFile(PlatformFile(it.path), it.name, it.isPdf) })
                } finally {
                    reading = false
                }
            }
        }
    }

    /** Viagem cujo período cobre a data lida do PDF. */
    fun suggestedTrip(trips: List<Trip>): Trip? {
        val date = imported?.draft?.startDate ?: return null
        return trips.firstOrNull { date in it.startDate..it.endDate }
    }

    /** Leva a leitura para o formulário de uma reserva nova na viagem escolhida. */
    fun createIn(tripId: String, onOpen: (String) -> Unit) {
        val result = imported ?: return
        importer.setPending(result)
        incoming.clear()
        onOpen(tripId)
    }

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

/** Arquivos compartilhados de outro app: criar uma reserva a partir do PDF ou anexar a uma existente (RF-44, RF-50). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(
    onDone: (String) -> Unit,
    onCreateReservation: (tripId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ShareTargetViewModel = koinViewModel(),
) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
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
            if (viewModel.saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            val tripList = trips ?: return@Column
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                if (files.any { it.isPdf } && tripList.isNotEmpty()) {
                    item(key = "new-header") {
                        Column {
                            SectionHeader(stringResource(Res.string.share_new_reservation))
                            Text(
                                stringResource(if (viewModel.reading) Res.string.import_reading else Res.string.share_new_reservation_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            if (viewModel.reading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                    }
                    if (!viewModel.reading) {
                        val suggested = viewModel.suggestedTrip(tripList)
                        val ordered = listOfNotNull(suggested) + (tripList - setOfNotNull(suggested))
                        items(ordered, key = { "new:${it.id}" }) { trip ->
                            ListItem(
                                modifier = Modifier.clickable { viewModel.createIn(trip.id, onCreateReservation) },
                                leadingContent = { TypeBadge("➕") },
                                headlineContent = { Text(trip.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            TimeFormat.range(trip.startDate, trip.endDate),
                                            if (trip == suggested) stringResource(Res.string.share_suggested_trip) else null,
                                        ).joinToString(" · "),
                                    )
                                },
                            )
                        }
                    }
                    item(key = "existing-header") { SectionHeader(stringResource(Res.string.share_attach_existing), Modifier.padding(top = 8.dp)) }
                }
                val list = groups.orEmpty()
                if (groups != null && list.isEmpty()) {
                    item(key = "empty") { Text(stringResource(Res.string.share_no_reservations), modifier = Modifier.padding(16.dp)) }
                }
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
