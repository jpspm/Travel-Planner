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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.ui.place.PlaceDraft
import com.jpspm.travelplanner.ui.place.PlaceField
import com.jpspm.travelplanner.domain.repository.ActivityInput
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_delete
import com.jpspm.travelplanner.resources.action_save
import com.jpspm.travelplanner.resources.activity_delete_message
import com.jpspm.travelplanner.resources.activity_delete_title
import com.jpspm.travelplanner.resources.activity_edit_title
import com.jpspm.travelplanner.resources.activity_end_time
import com.jpspm.travelplanner.resources.activity_new_title
import com.jpspm.travelplanner.resources.activity_start_time
import com.jpspm.travelplanner.resources.activity_title
import com.jpspm.travelplanner.resources.error_end_time_before_start
import com.jpspm.travelplanner.resources.error_required
import com.jpspm.travelplanner.resources.field_date
import com.jpspm.travelplanner.resources.field_notes
import com.jpspm.travelplanner.resources.field_timezone
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.DateField
import com.jpspm.travelplanner.ui.components.FieldRow
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.TimeField
import com.jpspm.travelplanner.ui.components.TimeZoneField
import com.jpspm.travelplanner.resources.section_place
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class ActivityForm(
    val title: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val timeZoneId: String = TimeZone.currentSystemDefault().id,
    /** O usuário escolheu o fuso à mão; não sobrescrever ao trocar a data. */
    val timeZoneTouched: Boolean = false,
    val place: PlaceDraft? = null,
    val notes: String = "",
    val showErrors: Boolean = false,
) {
    val titleError get() = title.isBlank()
    val endBeforeStart get() = startTime != null && endTime != null && endTime < startTime
    val isValid get() = !titleError && date != null && !endBeforeStart
}

class ActivityEditorViewModel(
    private val tripId: String,
    private val activityId: String?,
    initialDate: String?,
    private val activities: ActivityRepository,
    private val destinationsRepo: DestinationRepository,
) : ViewModel() {

    var form by mutableStateOf(ActivityForm(date = initialDate?.let(LocalDate::parse)))
        private set
    var destinations by mutableStateOf(emptyList<Destination>())
        private set
    var otherPlace by mutableStateOf<LatLng?>(null)
        private set
    val isNew get() = activityId == null

    init {
        viewModelScope.launch {
            destinations = destinationsRepo.observeDestinations(tripId).first()
            // Sem destino com localização, a busca usa como referência outro lugar já salvo na viagem.
            otherPlace = activities.observeActivities(tripId).first()
                .firstNotNullOfOrNull { a -> a.place?.takeIf { it.hasPosition }?.let { LatLng(it.latitude!!, it.longitude!!) } }
            val existing = activityId?.let { activities.getActivity(it) }
            form = if (existing != null) {
                ActivityForm(
                    title = existing.title,
                    date = existing.date,
                    startTime = existing.startTime,
                    endTime = existing.endTime,
                    timeZoneId = existing.timeZoneId,
                    timeZoneTouched = true,
                    place = PlaceDraft.of(existing.place),
                    notes = existing.notes,
                )
            } else {
                form.copy(timeZoneId = defaultTimeZoneFor(form.date, destinations))
            }
        }
    }

    fun update(transform: (ActivityForm) -> ActivityForm) {
        form = transform(form)
    }

    fun setDate(date: LocalDate) = update {
        it.copy(date = date, timeZoneId = if (it.timeZoneTouched) it.timeZoneId else defaultTimeZoneFor(date, destinations))
    }

    /** Lugar escolhido na busca preenche o título vazio e sugere o fuso, se o usuário não escolheu outro. */
    fun setPlace(place: PlaceDraft?) = update {
        val zone = place?.input?.timeZoneId
        it.copy(
            place = place,
            title = it.title.ifBlank { place?.input?.name.orEmpty() },
            timeZoneId = if (zone != null && !it.timeZoneTouched) zone else it.timeZoneId,
        )
    }

    fun save(onDone: () -> Unit) {
        val f = form
        if (!f.isValid) {
            form = f.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            activities.save(
                tripId, activityId,
                ActivityInput(
                    date = f.date!!,
                    startTime = f.startTime,
                    endTime = f.endTime,
                    timeZoneId = f.timeZoneId,
                    title = f.title,
                    notes = f.notes,
                    place = f.place?.input ?: PlaceInput.None,
                ),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        activityId?.let { activities.delete(it) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorScreen(
    tripId: String,
    activityId: String?,
    date: String?,
    onBack: () -> Unit,
    viewModel: ActivityEditorViewModel = koinViewModel { parametersOf(tripId, activityId, date) },
) {
    val form = viewModel.form
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) Res.string.activity_new_title else Res.string.activity_edit_title)) },
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
            FormTextField(
                label = stringResource(Res.string.activity_title),
                value = form.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                isError = form.showErrors && form.titleError,
                supportingText = if (form.showErrors && form.titleError) stringResource(Res.string.error_required) else null,
            )
            DateField(label = stringResource(Res.string.field_date), date = form.date, onDateChange = viewModel::setDate)
            FieldRow(
                first = { m ->
                    TimeField(
                        label = stringResource(Res.string.activity_start_time),
                        time = form.startTime,
                        onTimeChange = { t -> viewModel.update { it.copy(startTime = t, endTime = if (t == null) null else it.endTime) } },
                        modifier = m,
                    )
                },
                second = { m ->
                    if (form.startTime != null) {
                        TimeField(
                            label = stringResource(Res.string.activity_end_time),
                            time = form.endTime,
                            onTimeChange = { t -> viewModel.update { it.copy(endTime = t) } },
                            modifier = m,
                            isError = form.showErrors && form.endBeforeStart,
                            supportingText = if (form.showErrors && form.endBeforeStart) stringResource(Res.string.error_end_time_before_start) else null,
                        )
                    }
                },
            )
            TimeZoneField(
                label = stringResource(Res.string.field_timezone),
                timeZoneId = form.timeZoneId,
                onTimeZoneChange = { z -> viewModel.update { it.copy(timeZoneId = z, timeZoneTouched = true) } },
                suggestions = timeZoneSuggestions(viewModel.destinations),
            )
            SectionHeader(stringResource(Res.string.section_place), Modifier.padding(top = 8.dp))
            PlaceField(
                draft = form.place,
                onChange = viewModel::setPlace,
                near = nearFor(form.date, viewModel.destinations) ?: viewModel.otherPlace,
                searchQuery = form.title,
            )
            FormTextField(
                label = stringResource(Res.string.field_notes),
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                singleLine = false,
                minLines = 3,
            )
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.activity_delete_title),
            message = stringResource(Res.string.activity_delete_message, form.title),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
