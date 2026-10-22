package com.jpspm.travelplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_cancel
import com.jpspm.travelplanner.resources.action_clear
import com.jpspm.travelplanner.resources.action_ok
import com.jpspm.travelplanner.resources.timezone_dialog_title
import com.jpspm.travelplanner.resources.timezone_search
import com.jpspm.travelplanner.resources.timezone_suggested
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** Campo somente leitura que abre um seletor ao ser tocado. */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = trailing,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        // Camada clicável por cima do campo, deixando livre a área do ícone final (ex.: limpar).
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (trailing != null) 48.dp else 0.dp)
                .clickable(role = Role.Button, onClick = onClick)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        label = label,
        value = date?.let { "${TimeFormat.weekdayShort(it.dayOfWeek)}, ${TimeFormat.date(it)}" }.orEmpty(),
        onClick = { open = true },
        modifier = modifier,
        trailing = { Icon(Icons.Default.DateRange, contentDescription = null) },
        isError = isError,
        supportingText = supportingText,
    )
    if (open) {
        DatePickerModal(initial = date, onPick = onDateChange, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: today()).toUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.utcMillisToDate()) }
                onDismiss()
            }) { Text(stringResource(Res.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    time: LocalTime?,
    onTimeChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        label = label,
        value = time?.let(TimeFormat::time).orEmpty(),
        onClick = { open = true },
        modifier = modifier,
        trailing = if (clearable && time != null) {
            {
                IconButton(onClick = { onTimeChange(null) }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.action_clear))
                }
            }
        } else null,
        isError = isError,
        supportingText = supportingText,
    )
    if (open) {
        val state = rememberTimePickerState(
            initialHour = time?.hour ?: 9,
            initialMinute = time?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime(state.hour, state.minute))
                    open = false
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

/**
 * Seletor de fuso IANA com busca. [suggestions] (fusos dos destinos da viagem e do aparelho)
 * aparecem no topo da lista.
 */
@Composable
fun TimeZoneField(
    label: String,
    timeZoneId: String,
    onTimeZoneChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val now = remember { Clock.System.now() }
    PickerField(
        label = label,
        value = TimeFormat.timeZoneOptionLabel(timeZoneId, now),
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        TimeZoneDialog(
            suggestions = suggestions,
            now = now,
            onSelect = { onTimeZoneChange(it); open = false },
            onDismiss = { open = false },
        )
    }
}

private val allZoneIds: List<String> by lazy {
    TimeZone.availableZoneIds.filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }.sorted()
}

@Composable
private fun TimeZoneDialog(
    suggestions: List<String>,
    now: Instant,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().replace(' ', '_')
        if (q.isEmpty()) allZoneIds else allZoneIds.filter { it.contains(q, ignoreCase = true) }
    }
    val suggested = suggestions.distinct().filter { query.isBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.timezone_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(Res.string.timezone_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    if (suggested.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(Res.string.timezone_suggested),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(suggested, key = { "s:$it" }) { id -> ZoneRow(id, now, onSelect) }
                        item { HorizontalDivider() }
                    }
                    items(filtered, key = { it }) { id -> ZoneRow(id, now, onSelect) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ZoneRow(id: String, now: Instant, onSelect: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(TimeFormat.timeZoneOptionLabel(id, now)) },
        modifier = Modifier.clickable { onSelect(id) },
    )
}

fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.utcMillisToDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
