package com.jpspm.travelplanner.ui.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.ui.place.PlaceDraft
import com.jpspm.travelplanner.ui.place.PlaceField
import com.jpspm.travelplanner.ui.place.PlacePickerDialog
import com.jpspm.travelplanner.resources.destination_search
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedButton
import com.jpspm.travelplanner.domain.repository.DestinationInput
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_delete
import com.jpspm.travelplanner.resources.action_save
import com.jpspm.travelplanner.resources.destination_arrival
import com.jpspm.travelplanner.resources.destination_country
import com.jpspm.travelplanner.resources.destination_delete_message
import com.jpspm.travelplanner.resources.destination_delete_title
import com.jpspm.travelplanner.resources.destination_departure
import com.jpspm.travelplanner.resources.destination_edit_title
import com.jpspm.travelplanner.resources.destination_name
import com.jpspm.travelplanner.resources.destination_new_title
import com.jpspm.travelplanner.resources.destination_timezone_hint
import com.jpspm.travelplanner.resources.error_end_before_start
import com.jpspm.travelplanner.resources.error_required
import com.jpspm.travelplanner.resources.field_timezone
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.DateField
import com.jpspm.travelplanner.ui.components.FieldRow
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.components.TimeZoneField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class DestinationForm(
    val name: String = "",
    val country: String = "",
    val arrivalDate: LocalDate? = null,
    val departureDate: LocalDate? = null,
    val timeZoneId: String = TimeZone.currentSystemDefault().id,
    val place: PlaceDraft? = null,
    val showErrors: Boolean = false,
) {
    val nameError get() = name.isBlank()
    val datesMissing get() = arrivalDate == null || departureDate == null
    val endBeforeStart get() = arrivalDate != null && departureDate != null && departureDate < arrivalDate
    val isValid get() = !nameError && !datesMissing && !endBeforeStart
}

class DestinationEditorViewModel(
    private val tripId: String,
    private val destinationId: String?,
    private val trips: TripRepository,
    private val destinations: DestinationRepository,
) : ViewModel() {

    var form by mutableStateOf(DestinationForm())
        private set
    var suggestions by mutableStateOf(emptyList<String>())
        private set
    val isNew get() = destinationId == null

    init {
        viewModelScope.launch {
            val others = destinations.observeDestinations(tripId).first().filter { it.id != destinationId }
            suggestions = timeZoneSuggestions(others)
            near = nearFor(null, others)
            val existing: Destination? = destinationId?.let { destinations.getDestination(it) }
            form = if (existing != null) {
                DestinationForm(existing.name, existing.country, existing.arrivalDate, existing.departureDate, existing.timeZoneId, place = PlaceDraft.of(existing.place))
            } else {
                // Novo destino começa onde o anterior termina (ou no início da viagem).
                val trip = trips.getTrip(tripId)
                val start = others.maxOfOrNull { it.departureDate } ?: trip?.startDate
                DestinationForm(arrivalDate = start, departureDate = trip?.endDate?.takeIf { start == null || it >= start } ?: start)
            }
        }
    }

    fun update(transform: (DestinationForm) -> DestinationForm) {
        form = transform(form)
    }

    /** Destinos já cadastrados com coordenadas, para centralizar a busca. */
    var near by mutableStateOf<LatLng?>(null)
        private set

    /** Cidade escolhida na busca preenche nome, país e fuso (RF-13). */
    fun setPlace(place: PlaceDraft?) = update { f ->
        val input = place?.input
        f.copy(
            place = place,
            name = input?.name ?: f.name,
            country = input?.address?.substringAfterLast(", ")?.takeIf { it.isNotBlank() } ?: f.country,
            timeZoneId = input?.timeZoneId ?: f.timeZoneId,
        )
    }

    fun save(onDone: () -> Unit) {
        val f = form
        if (!f.isValid) {
            form = f.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            destinations.save(tripId, destinationId, DestinationInput(f.name, f.country, f.arrivalDate!!, f.departureDate!!, f.timeZoneId, f.place?.input ?: PlaceInput.None))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        destinationId?.let { destinations.delete(it) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationEditorScreen(
    tripId: String,
    destinationId: String?,
    onBack: () -> Unit,
    viewModel: DestinationEditorViewModel = koinViewModel { parametersOf(tripId, destinationId) },
) {
    val form = viewModel.form
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) Res.string.destination_new_title else Res.string.destination_edit_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                    TextButton(onClick = { viewModel.save(onBack) }) { Text(stringResource(Res.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            // Buscar a cidade preenche nome, país, fuso e coordenadas; sem internet, basta digitar abaixo.
            if (form.place?.position != null) {
                PlaceField(draft = form.place, onChange = viewModel::setPlace, near = viewModel.near, searchQuery = form.name)
            } else {
                var picking by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { picking = true }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(stringResource(Res.string.destination_search), modifier = Modifier.padding(start = 8.dp))
                }
                if (picking) {
                    PlacePickerDialog(
                        initialQuery = form.name,
                        near = viewModel.near,
                        onPick = { picking = false; viewModel.setPlace(it) },
                        onManual = { picking = false },
                        onDismiss = { picking = false },
                    )
                }
            }
            FormTextField(
                label = stringResource(Res.string.destination_name),
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                isError = form.showErrors && form.nameError,
                supportingText = if (form.showErrors && form.nameError) stringResource(Res.string.error_required) else null,
                capitalization = KeyboardCapitalization.Words,
            )
            FormTextField(
                label = stringResource(Res.string.destination_country),
                value = form.country,
                onValueChange = { v -> viewModel.update { it.copy(country = v) } },
                capitalization = KeyboardCapitalization.Words,
            )
            FieldRow(
                first = { m ->
                    DateField(
                        label = stringResource(Res.string.destination_arrival),
                        date = form.arrivalDate,
                        onDateChange = { d ->
                            viewModel.update { it.copy(arrivalDate = d, departureDate = it.departureDate?.takeIf { dep -> dep >= d } ?: d) }
                        },
                        modifier = m,
                    )
                },
                second = { m ->
                    DateField(
                        label = stringResource(Res.string.destination_departure),
                        date = form.departureDate,
                        onDateChange = { d -> viewModel.update { it.copy(departureDate = d) } },
                        modifier = m,
                        isError = form.showErrors && form.endBeforeStart,
                        supportingText = if (form.showErrors && form.endBeforeStart) stringResource(Res.string.error_end_before_start) else null,
                    )
                },
            )
            TimeZoneField(
                label = stringResource(Res.string.field_timezone),
                timeZoneId = form.timeZoneId,
                onTimeZoneChange = { z -> viewModel.update { it.copy(timeZoneId = z) } },
                suggestions = viewModel.suggestions,
            )
            Text(
                stringResource(Res.string.destination_timezone_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.destination_delete_title),
            message = stringResource(Res.string.destination_delete_message, form.name),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
