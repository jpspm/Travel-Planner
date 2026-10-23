package com.jpspm.travelplanner.ui.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.domain.model.PhotoInput
import com.jpspm.travelplanner.domain.places.OpeningHours
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.PlaceCandidate
import com.jpspm.travelplanner.domain.repository.PlaceSearch
import com.jpspm.travelplanner.domain.repository.PlaceSearchException
import com.jpspm.travelplanner.domain.repository.RemotePhoto
import com.jpspm.travelplanner.resources.*
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.map.MapMarker
import com.jpspm.travelplanner.ui.map.PlacesMap
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class PlacePickerState(
    val query: String = "",
    val results: List<PlaceCandidate> = emptyList(),
    val searching: Boolean = false,
    /** A última busca foi a completa (Nominatim), não só as sugestões. */
    val fullSearchDone: Boolean = false,
    val error: Boolean = false,
    val selected: PlaceCandidate? = null,
    val loadingPhoto: Boolean = false,
    val photo: RemotePhoto? = null,
    /** Horário de funcionamento do lugar escolhido, quando conhecido (RF-40). */
    val openingHours: String? = null,
)

@OptIn(FlowPreview::class)
class PlacePickerViewModel(private val search: PlaceSearch) : ViewModel() {

    var state by mutableStateOf(PlacePickerState())
        private set

    private var near: LatLng? = null
    private val queries = MutableStateFlow("")
    private var photoJob: Job? = null

    init {
        // Sugestões ao digitar, com espera de 500 ms para não disparar uma busca por tecla.
        viewModelScope.launch {
            queries.debounce(500).distinctUntilChanged().collectLatest { q ->
                if (q.trim().length < 3) return@collectLatest
                runSearch(full = false, q.trim())
            }
        }
    }

    fun start(initialQuery: String, near: LatLng?) {
        this.near = near
        clearSelection()
        if (initialQuery.isNotBlank() && initialQuery != state.query) onQueryChange(initialQuery)
    }

    fun onQueryChange(query: String) {
        state = state.copy(query = query, fullSearchDone = false, error = false)
        queries.value = query
    }

    /** Busca completa por ação do usuário (tecla buscar), com nomes em português. */
    fun searchFull() {
        val q = state.query.trim()
        if (q.length < 2) return
        viewModelScope.launch { runSearch(full = true, q) }
    }

    private suspend fun runSearch(full: Boolean, query: String) {
        state = state.copy(searching = true, error = false)
        try {
            val results = if (full) search.search(query, near) else search.suggest(query, near)
            if (state.query.trim() != query) {
                state = state.copy(searching = false)
                return
            }
            // A busca completa vem primeiro; sugestões que não estão nela ficam depois.
            val merged = if (full) results + state.results.filter { r -> results.none { it.osmId == r.osmId } } else results
            state = state.copy(results = merged, searching = false, fullSearchDone = full || state.fullSearchDone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaceSearchException) {
            state = state.copy(searching = false, error = true)
        }
    }

    fun select(candidate: PlaceCandidate) {
        state = state.copy(selected = candidate, photo = null, openingHours = candidate.openingHours, loadingPhoto = true)
        photoJob?.cancel()
        photoJob = viewModelScope.launch {
            val details = try {
                search.details(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            state = state.copy(
                photo = details?.photo,
                openingHours = details?.openingHours ?: candidate.openingHours,
                loadingPhoto = false,
            )
        }
    }

    fun clearSelection() {
        photoJob?.cancel()
        state = state.copy(selected = null, photo = null, openingHours = null, loadingPhoto = false)
    }
}

/**
 * Busca de lugar em tela cheia (RF-10, RF-11, RF-12): campo de busca, mapa com os resultados e,
 * ao escolher um, a foto da Wikimedia antes de confirmar. Sem internet, permite digitar à mão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerDialog(
    initialQuery: String,
    near: LatLng?,
    onPick: (PlaceDraft) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val viewModel: PlacePickerViewModel = koinViewModel(key = "place-picker")
        androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.start(initialQuery, near) }
        val state = viewModel.state
        // Esconde o teclado sem tirar o foco: tirar o foco faz alguns teclados (Gboard) apagarem o texto preenchido.
        val keyboard = LocalSoftwareKeyboardController.current
        val galleryPicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
            val selected = state.selected
            if (file != null && selected != null) {
                val draft = PlaceDraft.of(selected, photo = null, openingHours = state.openingHours)
                onPick(draft.copy(input = draft.input.copy(photo = PhotoInput.Local(file)), photoModel = file, photoCredit = null))
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.place_search_title)) },
                    navigationIcon = { BackButton(onDismiss) },
                    actions = { TextButton(onClick = onManual) { Text(stringResource(Res.string.place_manual)) } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.place_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange(""); viewModel.clearSelection() }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.action_clear))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); viewModel.searchFull() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

                val selected = state.selected
                val markers = (if (selected != null) listOf(selected) else state.results).mapIndexed { i, c ->
                    MapMarker(
                        id = c.osmId ?: "$i",
                        position = c.position,
                        badge = if (selected != null) "★" else "${i + 1}",
                        description = c.name,
                        highlighted = selected != null,
                    )
                }
                PlacesMap(
                    markers = markers,
                    focus = selected?.position ?: if (markers.isEmpty()) near else null,
                    onMarkerClick = { marker -> keyboard?.hide(); state.results.firstOrNull { (it.osmId ?: "") == marker.id }?.let(viewModel::select) },
                    modifier = Modifier.fillMaxWidth().height(if (selected != null) 200.dp else 240.dp),
                )

                if (selected != null) {
                    SelectedPlace(
                        candidate = selected,
                        state = state,
                        onConfirm = { onPick(PlaceDraft.of(selected, state.photo, state.openingHours)) },
                        onGallery = { galleryPicker.launch() },
                        onBack = viewModel::clearSelection,
                    )
                } else {
                    ResultsList(
                        state = state,
                        // Fecha o teclado para a foto e os botões de confirmar ficarem visíveis.
                        onSelect = { keyboard?.hide(); viewModel.select(it) },
                        onFullSearch = { keyboard?.hide(); viewModel.searchFull() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsList(state: PlacePickerState, onSelect: (PlaceCandidate) -> Unit, onFullSearch: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.error) {
            item {
                Text(
                    stringResource(Res.string.place_search_offline),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        // As sugestões usam os nomes locais (ex.: "Colosseo"); a busca completa entende português.
        if (state.query.trim().length >= 2 && !state.fullSearchDone && !state.searching) {
            item {
                TextButton(onClick = onFullSearch, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(stringResource(Res.string.place_search_full, state.query.trim()), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        items(state.results.size) { index ->
            val candidate = state.results[index]
            ListItem(
                modifier = Modifier.clickable { onSelect(candidate) },
                leadingContent = {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                headlineContent = { Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(candidate.address, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            )
            HorizontalDivider()
        }
        if (state.fullSearchDone && state.results.isEmpty() && !state.searching) {
            item { Text(stringResource(Res.string.place_search_empty), modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun SelectedPlace(
    candidate: PlaceCandidate,
    state: PlacePickerState,
    onConfirm: () -> Unit,
    onGallery: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(112.dp),
            ) {
                when {
                    state.loadingPhoto -> Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                    state.photo != null -> AsyncImage(
                        model = state.photo.url,
                        contentDescription = candidate.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    )
                    else -> Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                Text(candidate.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.openingHours?.let { hours ->
            Text(
                stringResource(Res.string.place_opening_hours, OpeningHours.describe(hours)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.photo?.let { photo ->
            Text(
                stringResource(Res.string.place_photo_credit, listOf(photo.author, photo.license).filter(String::isNotBlank).joinToString(" · ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!state.loadingPhoto && state.photo == null) {
            Text(stringResource(Res.string.place_no_photo), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onConfirm, enabled = !state.loadingPhoto, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.place_use_this))
        }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.place_use_gallery_photo))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.place_back_to_results))
        }
    }
}

/** Campo de manual: nome e endereço digitados, para quando não há internet (RNF-02). */
@Composable
fun ManualPlaceFields(name: String, address: String, onChange: (String, String) -> Unit) {
    FormTextField(label = stringResource(Res.string.field_place_name), value = name, onValueChange = { onChange(it, address) })
    FormTextField(label = stringResource(Res.string.field_address), value = address, onValueChange = { onChange(name, it) })
}
