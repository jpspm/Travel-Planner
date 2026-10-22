package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.add_activity
import com.jpspm.travelplanner.resources.add_activity_hint
import com.jpspm.travelplanner.resources.add_on_date
import com.jpspm.travelplanner.resources.add_reservation_header
import com.jpspm.travelplanner.resources.reservation_pick_type
import com.jpspm.travelplanner.ui.components.emoji
import com.jpspm.travelplanner.ui.components.label
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** O que o botão de adicionar abre: em qual dia e se a atividade aparece entre as opções. */
data class AddRequest(val date: LocalDate?, val includeActivity: Boolean)

/** Opções de adicionar: atividade e todos os tipos de reserva, já no dia escolhido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    request: AddRequest,
    onActivity: (LocalDate?) -> Unit,
    onReservation: (ReservationType, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                request.date?.let { stringResource(Res.string.add_on_date, TimeFormat.weekdayDayMonth(it)) }
                    ?: stringResource(Res.string.reservation_pick_type),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            if (request.includeActivity) {
                ListItem(
                    colors = colors,
                    modifier = Modifier.clickable { onActivity(request.date) },
                    leadingContent = { Text("📍", style = MaterialTheme.typography.titleLarge) },
                    headlineContent = { Text(stringResource(Res.string.add_activity)) },
                    supportingContent = { Text(stringResource(Res.string.add_activity_hint)) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Text(
                    stringResource(Res.string.add_reservation_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            ReservationType.entries.forEach { type ->
                ListItem(
                    colors = colors,
                    modifier = Modifier.clickable { onReservation(type, request.date) },
                    leadingContent = { Text(type.emoji, style = MaterialTheme.typography.titleLarge) },
                    headlineContent = { Text(stringResource(type.label)) },
                )
            }
        }
    }
}
