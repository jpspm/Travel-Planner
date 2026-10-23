package com.jpspm.travelplanner.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.data.settings.AppSettings
import com.jpspm.travelplanner.data.settings.SettingsStore
import com.jpspm.travelplanner.platform.rememberNotificationPermission
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_ok
import com.jpspm.travelplanner.resources.reminders_activities
import com.jpspm.travelplanner.resources.reminders_allow
import com.jpspm.travelplanner.resources.reminders_battery_hint
import com.jpspm.travelplanner.resources.reminders_no_permission
import com.jpspm.travelplanner.resources.reminders_reservations
import com.jpspm.travelplanner.resources.reminders_title
import com.jpspm.travelplanner.resources.reminders_transit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

class RemindersViewModel(private val settings: SettingsStore) : ViewModel() {
    val state: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settings.update(transform) }
}

/**
 * Pede a permissão de notificações uma única vez, na primeira abertura (Android 13+). Depois disso,
 * o usuário pode liberar pela tela de Lembretes.
 */
@Composable
fun AskNotificationPermissionOnce(viewModel: RemindersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission()
    LaunchedEffect(state?.notificationPermissionAsked, permission.granted) {
        val current = state ?: return@LaunchedEffect
        if (!permission.granted && !current.notificationPermissionAsked) {
            viewModel.update { it.copy(notificationPermissionAsked = true) }
            permission.request()
        }
    }
}

/** Liga, desliga e escolhe a antecedência dos lembretes (RF-43). */
@Composable
fun RemindersDialog(viewModel: RemindersViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val permission = rememberNotificationPermission()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminders_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!permission.granted) {
                    Text(stringResource(Res.string.reminders_no_permission), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = permission.request) { Text(stringResource(Res.string.reminders_allow)) }
                }
                ReminderRow(
                    label = Res.string.reminders_transit,
                    enabled = s.reminderTransitEnabled,
                    minutes = s.reminderTransitMinutes,
                    options = listOf(60, 120, 180, 240, 360),
                    onEnabled = { v -> viewModel.update { it.copy(reminderTransitEnabled = v) } },
                    onMinutes = { v -> viewModel.update { it.copy(reminderTransitMinutes = v) } },
                )
                ReminderRow(
                    label = Res.string.reminders_reservations,
                    enabled = s.reminderReservationEnabled,
                    minutes = s.reminderReservationMinutes,
                    options = listOf(30, 60, 120, 1440),
                    onEnabled = { v -> viewModel.update { it.copy(reminderReservationEnabled = v) } },
                    onMinutes = { v -> viewModel.update { it.copy(reminderReservationMinutes = v) } },
                )
                ReminderRow(
                    label = Res.string.reminders_activities,
                    enabled = s.reminderActivityEnabled,
                    minutes = s.reminderActivityMinutes,
                    options = listOf(15, 30, 60, 120),
                    onEnabled = { v -> viewModel.update { it.copy(reminderActivityEnabled = v) } },
                    onMinutes = { v -> viewModel.update { it.copy(reminderActivityMinutes = v) } },
                )
                Text(
                    stringResource(Res.string.reminders_battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) } },
    )
}

@Composable
private fun ReminderRow(
    label: StringResource,
    enabled: Boolean,
    minutes: Int,
    options: List<Int>,
    onEnabled: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
        if (enabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                options.forEach { option ->
                    FilterChip(selected = option == minutes, onClick = { onMinutes(option) }, label = { Text(leadLabel(option)) })
                }
            }
        }
    }
}

/** `30 min antes`, `3 h antes`, `1 dia antes` */
private fun leadLabel(minutes: Int): String = when {
    minutes % 1440 == 0 -> "${minutes / 1440} dia antes"
    minutes < 60 -> "$minutes min antes"
    minutes % 60 == 0 -> "${minutes / 60} h antes"
    else -> "${minutes / 60} h ${minutes % 60} min antes"
}
