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
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.IdeaInput
import com.jpspm.travelplanner.domain.repository.IdeaRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_delete
import com.jpspm.travelplanner.resources.action_save
import com.jpspm.travelplanner.resources.error_required
import com.jpspm.travelplanner.resources.field_notes
import com.jpspm.travelplanner.resources.idea_delete_message
import com.jpspm.travelplanner.resources.idea_delete_title
import com.jpspm.travelplanner.resources.idea_edit_title
import com.jpspm.travelplanner.resources.idea_new_title
import com.jpspm.travelplanner.resources.idea_title
import com.jpspm.travelplanner.resources.section_place
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.place.PlaceDraft
import com.jpspm.travelplanner.ui.place.PlaceField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class IdeaForm(
    val title: String = "",
    val notes: String = "",
    val place: PlaceDraft? = null,
    val showErrors: Boolean = false,
)

class IdeaEditorViewModel(
    private val tripId: String,
    private val ideaId: String?,
    private val ideas: IdeaRepository,
    private val destinationsRepo: DestinationRepository,
) : ViewModel() {

    var form by mutableStateOf(IdeaForm())
        private set
    var destinations by mutableStateOf(emptyList<Destination>())
        private set
    val isNew get() = ideaId == null

    init {
        viewModelScope.launch {
            destinations = destinationsRepo.observeDestinations(tripId).first()
            ideaId?.let { ideas.get(it) }?.let { form = IdeaForm(it.title, it.notes, PlaceDraft.of(it.place)) }
        }
    }

    fun update(transform: (IdeaForm) -> IdeaForm) {
        form = transform(form)
    }

    /** Lugar escolhido na busca já preenche o título, se estiver vazio. */
    fun setPlace(place: PlaceDraft?) = update { it.copy(place = place, title = it.title.ifBlank { place?.input?.name.orEmpty() }) }

    fun save(onDone: () -> Unit) {
        val f = form
        if (f.title.isBlank()) {
            form = f.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            ideas.save(tripId, ideaId, IdeaInput(f.title, f.notes, f.place?.input ?: PlaceInput.None))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        ideaId?.let { ideas.delete(it) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaEditorScreen(
    tripId: String,
    ideaId: String?,
    onBack: () -> Unit,
    viewModel: IdeaEditorViewModel = koinViewModel { parametersOf(tripId, ideaId) },
) {
    val form = viewModel.form
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) Res.string.idea_new_title else Res.string.idea_edit_title)) },
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
                label = stringResource(Res.string.idea_title),
                value = form.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                isError = form.showErrors && form.title.isBlank(),
                supportingText = if (form.showErrors && form.title.isBlank()) stringResource(Res.string.error_required) else null,
            )
            SectionHeader(stringResource(Res.string.section_place), Modifier.padding(top = 8.dp))
            PlaceField(
                draft = form.place,
                onChange = viewModel::setPlace,
                near = nearFor(null, viewModel.destinations),
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
            title = stringResource(Res.string.idea_delete_title),
            message = stringResource(Res.string.idea_delete_message, form.title),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
