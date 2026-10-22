package com.jpspm.travelplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.agenda_no_time
import com.jpspm.travelplanner.resources.agenda_until
import com.jpspm.travelplanner.resources.attachments_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Horário com rótulo do fuso (RF-08). [zoneNames] mapeia fuso → nome do destino da viagem,
 * para mostrar `14:00 (Roma, UTC+1)` em vez de `14:00 (Rome, UTC+1)`.
 */
fun ZonedLocal.label(zoneNames: Map<String, String>): String =
    TimeFormat.timeWithZone(this, zoneNames[timeZoneId])

@Composable
fun AgendaItemRow(
    item: AgendaItem,
    zoneNames: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = if (highlighted) {
        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    }
    when (item) {
        is AgendaItem.ActivityItem -> {
            val activity = item.activity
            ListItem(
                colors = colors,
                modifier = modifier.clickable(onClick = onClick),
                leadingContent = { PlaceThumbnail(activity.place?.photoPath, "📍") },
                headlineContent = { Text(activity.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        val start = activity.start
                        val time = if (start == null) {
                            stringResource(Res.string.agenda_no_time)
                        } else {
                            val end = activity.end?.let { " – ${TimeFormat.time(it.dateTime.time)}" }.orEmpty()
                            val zone = TimeFormat.zoneLabel(start.timeZoneId, start.toInstant(), zoneNames[start.timeZoneId])
                            "${TimeFormat.time(start.dateTime.time)}$end ($zone)"
                        }
                        Text(time)
                        activity.place?.let { place ->
                            Text(
                                listOf(place.name.takeIf { it != activity.title }.orEmpty(), place.address)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingContent = trailing,
            )
        }

        is AgendaItem.ReservationItem -> {
            val reservation = item.reservation
            ListItem(
                colors = colors,
                modifier = modifier.clickable(onClick = onClick),
                leadingContent = { PlaceThumbnail(reservation.place?.photoPath, reservation.type.emoji) },
                overlineContent = { Text(stringResource(item.momentLabel)) },
                headlineContent = {
                    Text(
                        reservation.title.ifBlank { stringResource(reservation.type.label) },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column {
                        Text(item.moment.label(zoneNames))
                        val end = reservation.end
                        if (item.role == AgendaItem.ReservationItem.Role.START && end != null &&
                            !reservation.type.endIsSeparateAgendaItem
                        ) {
                            val dayShift = (end.date.toEpochDays() - item.moment.date.toEpochDays()).toInt()
                            val shift = if (dayShift != 0) " (${if (dayShift > 0) "+" else ""}$dayShift)" else ""
                            Text(stringResource(Res.string.agenda_until, end.label(zoneNames) + shift))
                        }
                        if (reservation.attachments.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    Res.plurals.attachments_count,
                                    reservation.attachments.size,
                                    reservation.attachments.size,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                trailingContent = trailing,
            )
        }
    }
}

/** Foto do lugar salva no aparelho (RF-11) ou, sem foto, o ícone do tipo. */
@Composable
private fun PlaceThumbnail(photoPath: String?, emoji: String) {
    if (photoPath != null) {
        AsyncImage(
            model = photoPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        TypeBadge(emoji)
    }
}
