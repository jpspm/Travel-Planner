package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import com.jpspm.travelplanner.platform.externalMapUri
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import com.jpspm.travelplanner.domain.planning.Conflict
import com.jpspm.travelplanner.domain.planning.OrderSuggestion
import com.jpspm.travelplanner.domain.model.TripStatus
import com.jpspm.travelplanner.domain.city.place
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateMapOf
import com.jpspm.travelplanner.domain.repository.MapScope
import com.jpspm.travelplanner.domain.repository.MapSnapshotRepository
import com.jpspm.travelplanner.platform.Connectivity
import com.jpspm.travelplanner.ui.map.StaticMapSync
import org.koin.compose.koinInject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.jpspm.travelplanner.ui.backup.BackupDialogs
import com.jpspm.travelplanner.ui.backup.BackupViewModel
import com.jpspm.travelplanner.ui.backup.formatSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.resources.*
import com.jpspm.travelplanner.ui.components.AgendaItemRow
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.LoadingScreen
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.DatePickerModal
import com.jpspm.travelplanner.ui.components.EmptyState
import com.jpspm.travelplanner.ui.components.TypeBadge
import com.jpspm.travelplanner.ui.components.emoji
import com.jpspm.travelplanner.ui.components.label
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class TripTab(val label: org.jetbrains.compose.resources.StringResource) {
    /** Só nas viagens passadas (RF-31). */
    SUMMARY(Res.string.tab_summary),
    ITINERARY(Res.string.tab_itinerary),
    IDEAS(Res.string.tab_ideas),
    RESERVATIONS(Res.string.tab_reservations),
    CITIES(Res.string.tab_cities),
    BUDGET(Res.string.tab_budget),
    CHECKLIST(Res.string.tab_checklist),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onNewActivity: (date: String?) -> Unit,
    onEditActivity: (String) -> Unit,
    onNewReservation: (ReservationType, date: String?) -> Unit,
    onOpenReservation: (String) -> Unit,
    onNewDestination: () -> Unit,
    onOpenCity: (String) -> Unit,
    onOpenIdea: (String?) -> Unit,
    onNewIdea: (kind: String?) -> Unit,
    onScheduleIdeaAsReservation: (ideaId: String, type: ReservationType, date: String) -> Unit,
    onOpenNearby: () -> Unit,
    viewModel: TripDetailViewModel = koinViewModel { parametersOf(tripId) },
    backupViewModel: BackupViewModel = koinViewModel(),
) {
    BackupDialogs(backupViewModel)
    var showStorage by remember { mutableStateOf(false) }
    var choosingCover by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip = state.trip
    var tab by rememberSaveable { mutableStateOf<TripTab?>(null) }
    var addingExpense by remember { mutableStateOf(false) }
    var choosingIdeaKind by remember { mutableStateOf(false) }
    val htmlExport: HtmlExportViewModel = koinViewModel()
    HtmlExportEffects(htmlExport)
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<AddRequest?>(null) }
    // Dia que está no topo da lista do roteiro: o botão de adicionar usa essa data.
    var itineraryDay by remember { mutableStateOf<kotlinx.datetime.LocalDate?>(null) }
    val coverPicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) viewModel.setCoverPhoto(file)
    }

    LaunchedEffect(state.loading, trip) {
        if (!state.loading && trip == null) onBack() // viagem excluída
    }
    if (trip == null) {
        LoadingScreen(onBack)
        return
    }
    StaticMapSync(tripId, trip.startDate, state.days)
    val snapshotRepository = koinInject<MapSnapshotRepository>()
    val snapshots by remember(tripId) { snapshotRepository.observe(tripId) }.collectAsStateWithLifecycle(emptyList())
    // Viagem passada abre no Resumo (RF-31); as outras, no Roteiro.
    val isPast = state.today?.let { trip.status(it) == TripStatus.PAST } == true
    val tabs = if (isPast) TripTab.entries else TripTab.entries - TripTab.SUMMARY
    val currentTab = tab?.takeIf { it in tabs } ?: tabs.first()
    val copySuffix = stringResource(Res.string.trip_copy_suffix)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(trip.name, maxLines = 1)
                            Text(
                                TimeFormat.range(trip.startDate, trip.endDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.trip_menu_edit)) },
                                onClick = { menuOpen = false; onEditTrip() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.trip_menu_cover)) },
                                onClick = { menuOpen = false; choosingCover = true },
                            )
                            if (trip.coverPhotoPath != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.trip_menu_remove_cover)) },
                                    onClick = { menuOpen = false; viewModel.setCoverPhoto(null) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.trip_menu_duplicate)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.duplicate("${trip.name} $copySuffix", onOpenTrip)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.trip_menu_share_html)) },
                                onClick = { menuOpen = false; htmlExport.export(tripId) },
                                enabled = !htmlExport.exporting,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.backup_export_trip)) },
                                onClick = { menuOpen = false; backupViewModel.export(listOf(tripId)) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.trip_menu_storage)) },
                                onClick = { menuOpen = false; viewModel.loadStorage(); showStorage = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.trip_menu_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    },
                )
                PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(currentTab), edgePadding = 8.dp) {
                    tabs.forEach { t ->
                        Tab(selected = currentTab == t, onClick = { tab = t }, text = { Text(stringResource(t.label)) })
                    }
                }
            }
        },
        floatingActionButton = {
            val fab: Pair<org.jetbrains.compose.resources.StringResource, () -> Unit>? = when (currentTab) {
                TripTab.ITINERARY -> Res.string.fab_add to {
                    val date = itineraryDay ?: state.today?.takeIf { it in trip.startDate..trip.endDate } ?: trip.startDate
                    adding = AddRequest(date, includeActivity = true)
                }
                TripTab.RESERVATIONS -> Res.string.fab_reservation to { adding = AddRequest(null, includeActivity = false) }
                TripTab.CITIES -> Res.string.fab_city to onNewDestination
                TripTab.BUDGET -> Res.string.fab_expense to { addingExpense = true }
                TripTab.IDEAS -> Res.string.fab_idea to { choosingIdeaKind = true }
                TripTab.SUMMARY, TripTab.CHECKLIST -> null
            }
            fab?.let { (label, action) ->
                ExtendedFloatingActionButton(
                    onClick = action,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(label)) },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (currentTab) {
            TripTab.SUMMARY -> SummaryTab(
                state = state,
                snapshots = snapshots,
                onOpenCity = onOpenCity,
                onShareHtml = { htmlExport.export(tripId) },
                exporting = htmlExport.exporting,
                modifier = modifier,
            )
            TripTab.ITINERARY -> ItineraryTab(
                trip = trip,
                days = state.days,
                zoneNames = state.zoneNames,
                today = state.today,
                onAddToDay = { adding = AddRequest(it, includeActivity = true) },
                onVisibleDayChange = { itineraryDay = it },
                onOpenActivity = onEditActivity,
                onOpenReservation = onOpenReservation,
                onMoveActivity = viewModel::moveActivity,
                onMoveActivityToDate = viewModel::moveActivityToDate,
                onDeleteActivity = viewModel::deleteActivity,
                conflicts = state.conflicts,
                orderSuggestions = state.orderSuggestions,
                itemTitles = state.itemTitles,
                onDismissConflict = viewModel::dismissConflict,
                onApplyOrder = viewModel::applyOrder,
                modifier = modifier,
            )
            TripTab.RESERVATIONS -> ReservationsTab(state.reservations, state.zoneNames, onOpenReservation, modifier)
            TripTab.CITIES -> CitiesTab(state.cities, onOpen = { onOpenCity(it.key) }, modifier = modifier)
            TripTab.BUDGET -> BudgetTab(
                tripId = tripId,
                onOpenReservation = onOpenReservation,
                addingExpense = addingExpense,
                onAddingExpenseChange = { addingExpense = it },
                modifier = modifier,
            )
            TripTab.CHECKLIST -> ChecklistTab(tripId, modifier)
            TripTab.IDEAS -> IdeasTab(
                tripId = tripId,
                defaultDate = itineraryDay ?: state.today?.takeIf { it in trip.startDate..trip.endDate } ?: trip.startDate,
                onOpenIdea = onOpenIdea,
                onOpenNearby = onOpenNearby,
                onScheduleReservation = { ideaId, type, date -> onScheduleIdeaAsReservation(ideaId, type, date.toString()) },
                modifier = modifier,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.trip_delete_title),
            message = stringResource(Res.string.trip_delete_message, trip.name),
            onConfirm = { viewModel.deleteTrip(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (showStorage) {
        AlertDialog(
            onDismissRequest = { showStorage = false },
            title = { Text(stringResource(Res.string.trip_menu_storage)) },
            text = {
                Text(
                    viewModel.storageBytes?.let { stringResource(Res.string.trip_storage_message, formatSize(it)) }
                        ?: stringResource(Res.string.trip_storage_loading)
                )
            },
            confirmButton = { TextButton(onClick = { showStorage = false }) { Text(stringResource(Res.string.action_ok)) } },
        )
    }
    if (choosingCover) {
        // Fotos dos lugares do roteiro e das cidades cadastradas, sem repetir.
        val placesWithPhoto = (state.days.flatMap { day -> day.items.mapNotNull { it.place() } } +
            state.destinations.mapNotNull { it.place })
            .filter { it.photoPath != null }
            .distinctBy { it.id }
        CoverPickerSheet(
            places = placesWithPhoto,
            onPlace = { choosingCover = false; viewModel.setCoverFromPlace(it.id) },
            onGallery = { choosingCover = false; coverPicker.launch() },
            onDismiss = { choosingCover = false },
        )
    }
    if (choosingIdeaKind) {
        AddItemSheet(
            request = AddRequest(date = null, includeActivity = true),
            onActivity = { choosingIdeaKind = false; onNewIdea(null) },
            onReservation = { type, _ -> choosingIdeaKind = false; onNewIdea(type.name) },
            onDismiss = { choosingIdeaKind = false },
            title = stringResource(Res.string.idea_pick_kind),
        )
    }
    adding?.let { request ->
        AddItemSheet(
            request = request,
            onActivity = { date -> adding = null; onNewActivity(date?.toString()) },
            onReservation = { type, date -> adding = null; onNewReservation(type, date?.toString()) },
            onDismiss = { adding = null },
        )
    }
}

@Composable
private fun ItineraryTab(
    trip: Trip,
    days: List<AgendaDay>,
    zoneNames: Map<String, String>,
    today: kotlinx.datetime.LocalDate?,
    onAddToDay: (kotlinx.datetime.LocalDate) -> Unit,
    onVisibleDayChange: (kotlinx.datetime.LocalDate?) -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenReservation: (String) -> Unit,
    onMoveActivity: (String, Boolean) -> Unit,
    onMoveActivityToDate: (String, kotlinx.datetime.LocalDate) -> Unit,
    onDeleteActivity: (String) -> Unit,
    conflicts: Map<kotlinx.datetime.LocalDate, List<Conflict>>,
    orderSuggestions: Map<kotlinx.datetime.LocalDate, OrderSuggestion>,
    itemTitles: Map<String, String>,
    onDismissConflict: (Conflict) -> Unit,
    onApplyOrder: (kotlinx.datetime.LocalDate, OrderSuggestion) -> Unit,
    modifier: Modifier,
) {
    var reviewingOrder by remember { mutableStateOf<Pair<kotlinx.datetime.LocalDate, OrderSuggestion>?>(null) }
    reviewingOrder?.let { (date, suggestion) ->
        OrderSuggestionDialog(
            suggestion = suggestion,
            itemTitles = itemTitles,
            onApply = { onApplyOrder(date, suggestion) },
            onDismiss = { reviewingOrder = null },
        )
    }
    val listState = rememberLazyListState()
    // Índice na lista de cada cabeçalho de dia e de cada item, para ligar lista e mapa.
    val indexOfKey = remember(days) {
        buildMap {
            var index = 0
            days.forEach { day ->
                put("day:${day.date}", index++)
                if (day.items.isEmpty()) index++
                day.items.forEach { put(it.key, index++) }
            }
        }
    }
    // Durante a viagem, abre o roteiro já no dia de hoje (RF-09).
    LaunchedEffect(days.isNotEmpty()) {
        indexOfKey["day:$today"]?.takeIf { it > 0 }?.let { listState.scrollToItem(it) }
    }
    val visibleDay by remember(days) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            days.lastOrNull { (indexOfKey["day:${it.date}"] ?: Int.MAX_VALUE) <= first }?.date ?: days.firstOrNull()?.date
        }
    }
    LaunchedEffect(visibleDay) { onVisibleDayChange(visibleDay) }
    // Altura medida de cada item (por índice), guardada para o cálculo não depender do que está visível agora.
    val itemHeights = remember(days) { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(listState, days) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { sizes -> sizes.forEach { (index, size) -> itemHeights[index] = size } }
    }
    val fabSpacePx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val endSpacerPx by remember(days) {
        derivedStateOf {
            val lastStart = days.lastOrNull()?.let { indexOfKey["day:${it.date}"] } ?: return@derivedStateOf fabSpacePx
            val endIndex = indexOfKey.values.maxOrNull() ?: lastStart
            val lastBlock = (lastStart..endIndex).sumOf { itemHeights[it] ?: 0 }
            (listState.layoutInfo.viewportSize.height - lastBlock).coerceAtLeast(fabSpacePx)
        }
    }
    var movingActivity by remember { mutableStateOf<Activity?>(null) }
    var deletingActivity by remember { mutableStateOf<Activity?>(null) }
    var mapExpanded by rememberSaveable { mutableStateOf(true) }
    var mapWholeTrip by rememberSaveable { mutableStateOf(false) }
    val mapped = remember(days) { mappedItems(days) }
    val online by koinInject<Connectivity>().online.collectAsStateWithLifecycle()
    val snapshotRepository = koinInject<MapSnapshotRepository>()
    val snapshots by remember(trip.id) { snapshotRepository.observe(trip.id) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Column(modifier) {
        if (mapped.isNotEmpty()) {
            ItineraryMap(
                items = mapped,
                visibleDay = visibleDay,
                tripStart = trip.startDate,
                showWholeTrip = mapWholeTrip,
                onShowWholeTripChange = { mapWholeTrip = it },
                expanded = mapExpanded,
                onExpandedChange = { mapExpanded = it },
                onMarkerClick = { item -> indexOfKey[item.itemKey]?.let { scope.launch { listState.animateScrollToItem(it) } } },
                // Sem internet, mostra a imagem salva do mapa (RNF-02).
                offlineImage = if (online) null else snapshots.firstOrNull {
                    if (mapWholeTrip) it.scope == MapScope.TRIP else it.scope == MapScope.DAY && it.date == visibleDay
                }?.filePath,
                offline = !online,
            )
        } else {
            Text(
                stringResource(Res.string.map_hint_no_places),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            days.forEach { day ->
                item(key = "day:${day.date}") {
                    DayHeader(trip, day.date, isToday = day.date == today, onAdd = { onAddToDay(day.date) })
                    DayNotices(
                        conflicts = conflicts[day.date].orEmpty(),
                        suggestion = orderSuggestions[day.date],
                        itemTitles = itemTitles,
                        onDismiss = onDismissConflict,
                        onSuggestion = { orderSuggestions[day.date]?.let { reviewingOrder = day.date to it } },
                    )
                }
                if (day.items.isEmpty()) {
                    item(key = "empty:${day.date}") {
                        Text(
                            stringResource(Res.string.itinerary_empty_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(day.items, key = { _, it -> it.key }) { index, item ->
                    if (index > 0) WalkConnector(previous = day.items[index - 1], current = item)
                    when (item) {
                        is AgendaItem.ActivityItem -> AgendaItemRow(
                            item = item,
                            zoneNames = zoneNames,
                            onClick = { onOpenActivity(item.activity.id) },
                            trailing = {
                                ActivityMenu(
                                    activity = item.activity,
                                    onMoveUp = { onMoveActivity(item.activity.id, true) },
                                    onMoveDown = { onMoveActivity(item.activity.id, false) },
                                    onMoveToDate = { movingActivity = item.activity },
                                    onDelete = { deletingActivity = item.activity },
                                    onOpenInMaps = item.activity.place?.takeIf { it.hasPosition }?.let { place ->
                                        { runCatching { uriHandler.openUri(externalMapUri(place.latitude!!, place.longitude!!, place.name)) } }
                                    },
                                )
                            },
                        )
                        is AgendaItem.ReservationItem -> AgendaItemRow(
                            item = item,
                            zoneNames = zoneNames,
                            onClick = { onOpenReservation(item.reservation.id) },
                        )
                    }
                }
            }
            // Espaço final para o último dia conseguir chegar ao topo da lista; sem ele o mapa,
            // que segue o dia do topo, nunca mostraria os últimos dias.
            item(key = "end-spacer") {
                Spacer(Modifier.height(with(LocalDensity.current) { endSpacerPx.toDp() }))
            }
        }
    }

    movingActivity?.let { activity ->
        DatePickerModal(
            initial = activity.date,
            onPick = { onMoveActivityToDate(activity.id, it) },
            onDismiss = { movingActivity = null },
        )
    }
    deletingActivity?.let { activity ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.activity_delete_title),
            message = stringResource(Res.string.activity_delete_message, activity.title),
            onConfirm = { onDeleteActivity(activity.id) },
            onDismiss = { deletingActivity = null },
        )
    }
}

@Composable
private fun DayHeader(trip: Trip, date: kotlinx.datetime.LocalDate, isToday: Boolean, onAdd: () -> Unit) {
    val dayNumber = (date.toEpochDays() - trip.startDate.toEpochDays()).toInt() + 1
    val inTrip = date in trip.startDate..trip.endDate
    Column {
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                val title = if (inTrip) stringResource(Res.string.itinerary_day_n, dayNumber) else stringResource(Res.string.itinerary_outside_trip)
                Text(
                    if (isToday) "$title · ${stringResource(Res.string.itinerary_today)}" else title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    TimeFormat.weekdayDayMonth(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.itinerary_add_to_day, TimeFormat.dayMonth(date)))
            }
        }
    }
}

@Composable
private fun ActivityMenu(
    activity: Activity,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToDate: () -> Unit,
    onDelete: () -> Unit,
    onOpenInMaps: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        // A ordem manual só vale para atividades sem horário; as demais seguem o relógio.
        if (activity.startTime == null) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.activity_move_up)) }, onClick = { open = false; onMoveUp() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.activity_move_down)) }, onClick = { open = false; onMoveDown() })
        }
        DropdownMenuItem(text = { Text(stringResource(Res.string.activity_move_to_day)) }, onClick = { open = false; onMoveToDate() })
        if (onOpenInMaps != null) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.place_open_in_maps)) }, onClick = { open = false; onOpenInMaps() })
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) },
            onClick = { open = false; onDelete() },
        )
    }
}

@Composable
private fun ReservationsTab(
    reservations: List<Reservation>,
    zoneNames: Map<String, String>,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    if (reservations.isEmpty()) {
        EmptyState(
            emoji = "🎫",
            title = stringResource(Res.string.reservations_empty_title),
            message = stringResource(Res.string.reservations_empty_message),
            modifier = modifier,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = modifier) {
        items(reservations, key = { it.id }) { reservation ->
            ListItem(
                modifier = Modifier.clickable { onOpen(reservation.id) },
                leadingContent = { TypeBadge(reservation.type.emoji) },
                overlineContent = { Text(stringResource(reservation.type.label)) },
                headlineContent = { Text(reservation.title.ifBlank { stringResource(reservation.type.label) }) },
                supportingContent = {
                    Column {
                        val start = reservation.start
                        Text(
                            if (start != null) "${TimeFormat.weekdayDayMonth(start.date)} · ${start.label(zoneNames)}"
                            else stringResource(Res.string.reservation_no_date)
                        )
                        val extras = listOfNotNull(
                            reservation.priceCents?.let(Money::format),
                            reservation.attachments.size.takeIf { it > 0 }?.let {
                                pluralStringResource(Res.plurals.attachments_count, it, it)
                            },
                        )
                        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
}

