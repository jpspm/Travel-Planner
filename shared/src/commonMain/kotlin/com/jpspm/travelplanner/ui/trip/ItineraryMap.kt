package com.jpspm.travelplanner.ui.trip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.map_collapse
import com.jpspm.travelplanner.resources.map_day
import com.jpspm.travelplanner.resources.map_empty
import com.jpspm.travelplanner.resources.map_offline_image
import com.jpspm.travelplanner.resources.map_offline_none
import com.jpspm.travelplanner.resources.map_offline_saved
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.resources.map_expand
import com.jpspm.travelplanner.resources.map_title_day
import com.jpspm.travelplanner.resources.map_title_trip
import com.jpspm.travelplanner.resources.map_trip
import com.jpspm.travelplanner.ui.map.MapMarker
import com.jpspm.travelplanner.ui.map.PlacesMap
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Lugar do roteiro com coordenadas, ligado ao item da lista para rolar até ele ao tocar no pino. */
data class MappedItem(val itemKey: String, val date: LocalDate, val place: Place, val position: LatLng)

fun AgendaItem.mappedPlace(): Place? = when (this) {
    is AgendaItem.ActivityItem -> activity.place
    // O check-out/devolução repete o local do check-in; só o início entra no mapa.
    is AgendaItem.ReservationItem -> reservation.place.takeIf { role == AgendaItem.ReservationItem.Role.START }
}

fun mappedItems(days: List<AgendaDay>): List<MappedItem> = days.flatMap { day ->
    day.items.mapNotNull { item ->
        val place = item.mappedPlace() ?: return@mapNotNull null
        val lat = place.latitude ?: return@mapNotNull null
        val lon = place.longitude ?: return@mapNotNull null
        MappedItem(item.key, day.date, place, LatLng(lat, lon))
    }
}

/**
 * Mapa no topo do roteiro (RF-14): no modo Dia mostra os lugares do dia visível na lista, numerados
 * na ordem do roteiro; no modo Viagem, todos os lugares, com o número do dia. Pode ser recolhido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryMap(
    items: List<MappedItem>,
    visibleDay: LocalDate?,
    tripStart: LocalDate,
    showWholeTrip: Boolean,
    onShowWholeTripChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMarkerClick: (MappedItem) -> Unit,
    offlineImage: String? = null,
    offline: Boolean = false,
) {
    val dayItems = items.filter { it.date == visibleDay }
    val shown = if (showWholeTrip) items else dayItems
    val markers = shown.mapIndexed { index, mapped ->
        val dayNumber = (mapped.date.toEpochDays() - tripStart.toEpochDays()).toInt() + 1
        MapMarker(
            id = mapped.itemKey,
            position = mapped.position,
            badge = if (showWholeTrip) "$dayNumber" else "${index + 1}",
            description = mapped.place.name,
        )
    }.distinctBy { it.id }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                if (showWholeTrip) stringResource(Res.string.map_title_trip)
                else stringResource(Res.string.map_title_day, visibleDay?.let(TimeFormat::weekdayDayMonth).orEmpty()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (expanded) {
                SingleChoiceSegmentedButtonRow(Modifier.padding(end = 4.dp)) {
                    SegmentedButton(
                        selected = !showWholeTrip,
                        onClick = { onShowWholeTripChange(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = {},
                    ) { Text(stringResource(Res.string.map_day)) }
                    SegmentedButton(
                        selected = showWholeTrip,
                        onClick = { onShowWholeTripChange(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = {},
                    ) { Text(stringResource(Res.string.map_trip)) }
                }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) Res.string.map_collapse else Res.string.map_expand),
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                if (offlineImage != null) {
                    AsyncImage(
                        model = offlineImage,
                        contentDescription = stringResource(Res.string.map_offline_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                } else {
                    PlacesMap(
                        markers = markers,
                        onMarkerClick = { marker -> shown.firstOrNull { it.itemKey == marker.id }?.let(onMarkerClick) },
                        // Dia sem lugares: mostra a viagem toda como referência.
                        focus = if (markers.isEmpty()) items.firstOrNull()?.position else null,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
                if (offline) {
                    Text(
                        stringResource(if (offlineImage != null) Res.string.map_offline_saved else Res.string.map_offline_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (markers.isEmpty()) {
                    Text(
                        stringResource(Res.string.map_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
