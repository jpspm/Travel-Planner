package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.planning.Conflict
import com.jpspm.travelplanner.domain.planning.OrderSuggestion
import com.jpspm.travelplanner.domain.planning.WalkEstimate
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_cancel
import com.jpspm.travelplanner.resources.conflict_dismiss
import com.jpspm.travelplanner.resources.conflict_during_transit
import com.jpspm.travelplanner.resources.conflict_night_without_lodging
import com.jpspm.travelplanner.resources.conflict_outside_trip
import com.jpspm.travelplanner.resources.conflict_overlap
import com.jpspm.travelplanner.resources.order_apply
import com.jpspm.travelplanner.resources.order_chip
import com.jpspm.travelplanner.resources.order_dialog_message
import com.jpspm.travelplanner.resources.order_dialog_title
import com.jpspm.travelplanner.resources.walk_estimate
import com.jpspm.travelplanner.resources.walk_estimate_long
import org.jetbrains.compose.resources.stringResource

/** Avisos de conflito e a sugestão de ordem logo abaixo do cabeçalho do dia (RF-37, RF-39). */
@Composable
fun DayNotices(
    conflicts: List<Conflict>,
    suggestion: OrderSuggestion?,
    itemTitles: Map<String, String>,
    onDismiss: (Conflict) -> Unit,
    onSuggestion: () -> Unit,
) {
    if (conflicts.isEmpty() && suggestion == null) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        conflicts.forEach { conflict -> ConflictRow(conflict, itemTitles, onDismiss = { onDismiss(conflict) }) }
        suggestion?.let {
            AssistChip(
                onClick = onSuggestion,
                label = { Text(stringResource(Res.string.order_chip, WalkEstimate.formatDistance(it.savedKm))) },
            )
        }
    }
}

@Composable
private fun ConflictRow(conflict: Conflict, titles: Map<String, String>, onDismiss: () -> Unit) {
    fun title(key: String) = titles[key].orEmpty()
    val text = when (conflict) {
        is Conflict.Overlap -> stringResource(Res.string.conflict_overlap, title(conflict.first), title(conflict.second))
        is Conflict.DuringTransit -> stringResource(Res.string.conflict_during_transit, title(conflict.activity), title(conflict.transit))
        is Conflict.NightWithoutLodging -> stringResource(Res.string.conflict_night_without_lodging)
        is Conflict.OutsideTrip -> stringResource(Res.string.conflict_outside_trip)
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.conflict_dismiss), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Distância e tempo a pé estimados entre o item anterior e este, quando os dois têm localização (RF-38). */
@Composable
fun WalkConnector(previous: AgendaItem, current: AgendaItem) {
    val a = previous.place()?.let { p -> if (p.latitude != null && p.longitude != null) LatLng(p.latitude, p.longitude) else null }
    val b = current.place()?.let { p -> if (p.latitude != null && p.longitude != null) LatLng(p.latitude, p.longitude) else null }
    if (a == null || b == null) return
    val estimate = WalkEstimate.between(a, b) ?: return
    val distance = WalkEstimate.formatDistance(estimate.distanceKm)
    Text(
        if (estimate.isLong) stringResource(Res.string.walk_estimate_long, distance)
        else stringResource(Res.string.walk_estimate, distance, WalkEstimate.formatMinutes(estimate.minutes)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 76.dp, top = 2.dp, bottom = 2.dp),
    )
}

/** Confirmação da ordem sugerida: mostra a nova ordem e quanto encurta (nunca aplica sozinho, RNF-25). */
@Composable
fun OrderSuggestionDialog(
    suggestion: OrderSuggestion,
    itemTitles: Map<String, String>,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.order_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        Res.string.order_dialog_message,
                        WalkEstimate.formatDistance(suggestion.currentKm * WalkEstimate.STREET_FACTOR),
                        WalkEstimate.formatDistance(suggestion.suggestedKm * WalkEstimate.STREET_FACTOR),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                suggestion.activityIds.forEachIndexed { i, id ->
                    Text("${i + 1}. ${itemTitles["a:$id"].orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(); onDismiss() }) { Text(stringResource(Res.string.order_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
