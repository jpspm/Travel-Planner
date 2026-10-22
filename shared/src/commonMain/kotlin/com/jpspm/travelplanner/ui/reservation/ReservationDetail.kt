package com.jpspm.travelplanner.ui.reservation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.resources.*
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.LoadingScreen
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.TypeBadge
import com.jpspm.travelplanner.ui.components.codeLabel
import com.jpspm.travelplanner.ui.components.emoji
import com.jpspm.travelplanner.ui.components.endLabel
import com.jpspm.travelplanner.ui.components.label
import com.jpspm.travelplanner.ui.components.placeLabel
import com.jpspm.travelplanner.ui.components.startLabel
import com.jpspm.travelplanner.domain.city.tripZoneNames
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class ReservationDetailUiState(
    val loading: Boolean = true,
    val reservation: Reservation? = null,
    val zoneNames: Map<String, String> = emptyMap(),
    val adding: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReservationDetailViewModel(
    private val reservationId: String,
    private val reservations: ReservationRepository,
    destinations: DestinationRepository,
    activities: ActivityRepository,
) : ViewModel() {

    private val adding = kotlinx.coroutines.flow.MutableStateFlow(false)

    val state: StateFlow<ReservationDetailUiState> = reservations.observeReservation(reservationId)
        .flatMapLatest { reservation ->
            val zones = if (reservation == null) flowOf(emptyMap())
            else combine(
                destinations.observeDestinations(reservation.tripId),
                activities.observeActivities(reservation.tripId),
                reservations.observeReservations(reservation.tripId),
                ::tripZoneNames,
            )
            combine(zones, adding) { zoneNames, isAdding ->
                ReservationDetailUiState(loading = false, reservation = reservation, zoneNames = zoneNames, adding = isAdding)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationDetailUiState())

    fun addAttachment(file: PlatformFile, kind: AttachmentKind) = viewModelScope.launch {
        adding.value = true
        try {
            reservations.addAttachment(reservationId, file, kind)
        } finally {
            adding.value = false
        }
    }

    fun deleteAttachment(id: String) = viewModelScope.launch { reservations.deleteAttachment(id) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        reservations.delete(reservationId)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationDetailScreen(
    reservationId: String,
    onBack: () -> Unit,
    onEdit: (tripId: String) -> Unit,
    onOpenAttachment: (String) -> Unit,
    viewModel: ReservationDetailViewModel = koinViewModel { parametersOf(reservationId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reservation = state.reservation
    var confirmDelete by remember { mutableStateOf(false) }
    var deletingAttachment by remember { mutableStateOf<Attachment?>(null) }

    LaunchedEffect(state.loading, reservation) {
        if (!state.loading && reservation == null) onBack()
    }
    if (reservation == null) {
        LoadingScreen(onBack)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(reservation.type.label)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { onEdit(reservation.tripId) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.action_edit))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                TypeBadge(reservation.type.emoji)
                Text(
                    reservation.title.ifBlank { stringResource(reservation.type.label) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            AttachmentsBlock(
                items = reservation.attachments.map { AttachmentItem(it.id, it.kind, it.fileName, it.filePath) },
                busy = state.adding,
                onOpen = { onOpenAttachment(it.key) },
                onRemove = { item -> deletingAttachment = reservation.attachments.firstOrNull { it.id == item.key } },
                onAdd = viewModel::addAttachment,
            )

            SectionHeader(stringResource(Res.string.section_details), Modifier.padding(top = 16.dp))
            reservation.start?.let { DetailRow(stringResource(reservation.type.startLabel), it.full(state.zoneNames)) }
            reservation.end?.let { DetailRow(stringResource(reservation.type.endLabel), it.full(state.zoneNames)) }
            reservation.type.fields.forEach { field ->
                reservation.detail(field.key).takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(field.label), it) }
            }
            reservation.place?.address?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(reservation.type.placeLabel), it) }
            reservation.confirmationCode.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(reservation.type.codeLabel), it) }
            reservation.priceCents?.let { DetailRow(stringResource(Res.string.field_price), Money.format(it)) }
            reservation.notes.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(Res.string.field_notes), it) }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.reservation_delete_title),
            message = stringResource(Res.string.reservation_delete_message, reservation.title),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    deletingAttachment?.let { attachment ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.attachment_delete_title),
            message = stringResource(Res.string.attachment_delete_message, attachment.fileName),
            onConfirm = { viewModel.deleteAttachment(attachment.id) },
            onDismiss = { deletingAttachment = null },
        )
    }
}

private fun ZonedLocal.full(zoneNames: Map<String, String>): String =
    "${TimeFormat.weekdayDayMonth(date)} ${date.year} · ${label(zoneNames)}"

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

