package com.jpspm.travelplanner.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.repository.TripInput
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_save
import com.jpspm.travelplanner.resources.error_end_before_start
import com.jpspm.travelplanner.resources.error_required
import com.jpspm.travelplanner.resources.trip_edit_title
import com.jpspm.travelplanner.resources.trip_end_date
import com.jpspm.travelplanner.resources.trip_name
import com.jpspm.travelplanner.resources.trip_new_title
import com.jpspm.travelplanner.resources.trip_notes
import com.jpspm.travelplanner.resources.trip_start_date
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.DateField
import com.jpspm.travelplanner.ui.components.FieldRow
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.components.today
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class TripForm(
    val name: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val notes: String = "",
    val showErrors: Boolean = false,
) {
    val nameError: Boolean get() = name.isBlank()
    val datesMissing: Boolean get() = startDate == null || endDate == null
    val endBeforeStart: Boolean get() = startDate != null && endDate != null && endDate < startDate
    val isValid: Boolean get() = !nameError && !datesMissing && !endBeforeStart
}

class TripEditorViewModel(
    private val tripId: String?,
    private val trips: TripRepository,
) : ViewModel() {

    var form by mutableStateOf(TripForm())
        private set

    val isNew: Boolean get() = tripId == null

    init {
        if (tripId != null) viewModelScope.launch {
            trips.getTrip(tripId)?.let {
                form = TripForm(name = it.name, startDate = it.startDate, endDate = it.endDate, notes = it.notes)
            }
        }
    }

    fun update(transform: (TripForm) -> TripForm) {
        form = transform(form)
    }

    fun setStartDate(date: LocalDate) {
        // Ajusta o fim para não ficar antes do início.
        update { it.copy(startDate = date, endDate = it.endDate?.takeIf { end -> end >= date } ?: date) }
    }

    fun save(onSaved: (String) -> Unit) {
        val f = form
        if (!f.isValid) {
            form = f.copy(showErrors = true)
            return
        }
        val input = TripInput(f.name, f.startDate!!, f.endDate!!, f.notes)
        viewModelScope.launch {
            val id = tripId?.also { trips.update(it, input) } ?: trips.create(input)
            onSaved(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditorScreen(
    tripId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: TripEditorViewModel = koinViewModel { parametersOf(tripId) },
) {
    val form = viewModel.form
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) Res.string.trip_new_title else Res.string.trip_edit_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }) { Text(stringResource(Res.string.action_save)) }
                },
            )
        },
    ) { padding ->
        LaunchedEffect(Unit) {
            if (viewModel.isNew && form.startDate == null) viewModel.setStartDate(today())
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            FormTextField(
                label = stringResource(Res.string.trip_name),
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                isError = form.showErrors && form.nameError,
                supportingText = if (form.showErrors && form.nameError) stringResource(Res.string.error_required) else null,
                capitalization = KeyboardCapitalization.Words,
            )
            FieldRow(
                first = { m ->
                    DateField(
                        label = stringResource(Res.string.trip_start_date),
                        date = form.startDate,
                        onDateChange = viewModel::setStartDate,
                        modifier = m,
                    )
                },
                second = { m ->
                    DateField(
                        label = stringResource(Res.string.trip_end_date),
                        date = form.endDate,
                        onDateChange = { d -> viewModel.update { it.copy(endDate = d) } },
                        modifier = m,
                        isError = form.showErrors && form.endBeforeStart,
                        supportingText = if (form.showErrors && form.endBeforeStart) stringResource(Res.string.error_end_before_start) else null,
                    )
                },
            )
            FormTextField(
                label = stringResource(Res.string.trip_notes),
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                singleLine = false,
                minLines = 4,
            )
        }
    }
}
