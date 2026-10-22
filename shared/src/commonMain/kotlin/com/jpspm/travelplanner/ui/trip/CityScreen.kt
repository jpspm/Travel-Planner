package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_edit
import com.jpspm.travelplanner.resources.city_no_items
import com.jpspm.travelplanner.ui.components.AgendaItemRow
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.LoadingScreen
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.map.MapMarker
import com.jpspm.travelplanner.ui.map.PlacesMap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Uma cidade da viagem: mapa só dela e o roteiro filtrado, dia a dia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityScreen(
    tripId: String,
    cityKey: String,
    onBack: () -> Unit,
    onEditDestination: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenReservation: (String) -> Unit,
    viewModel: TripDetailViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val city = state.cities.firstOrNull { it.key == cityKey }
    // A cidade some se todos os lugares dela forem removidos.
    LaunchedEffect(state.loading, city) { if (!state.loading && city == null) onBack() }
    if (city == null) {
        LoadingScreen(onBack)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(city.name, maxLines = 1)
                        val start = city.startDate
                        val end = city.endDate
                        if (start != null && end != null) {
                            Text(
                                TimeFormat.range(start, end),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    city.destination?.let { destination ->
                        IconButton(onClick = { onEditDestination(destination.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val markers = city.items.mapNotNull { item ->
            val p = item.place()?.takeIf { it.hasPosition } ?: return@mapNotNull null
            p to item
        }.distinctBy { it.first.id }.mapIndexed { index, (p, _) ->
            MapMarker(p.id, LatLng(p.latitude!!, p.longitude!!), "${index + 1}", p.name)
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "map") {
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    PlacesMap(
                        markers = markers,
                        focus = if (markers.isEmpty()) city.center else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (city.items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(Res.string.city_no_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            city.items.groupBy { it.date }.forEach { (date, items) ->
                item(key = "day:$date") {
                    HorizontalDivider()
                    SectionHeader(TimeFormat.weekdayDayMonth(date), Modifier.padding(top = 4.dp))
                }
                items(items, key = { it.key }) { item ->
                    AgendaItemRow(
                        item = item,
                        zoneNames = state.zoneNames,
                        onClick = {
                            when (item) {
                                is AgendaItem.ActivityItem -> onOpenActivity(item.activity.id)
                                is AgendaItem.ReservationItem -> onOpenReservation(item.reservation.id)
                            }
                        },
                    )
                }
            }
        }
    }
}
