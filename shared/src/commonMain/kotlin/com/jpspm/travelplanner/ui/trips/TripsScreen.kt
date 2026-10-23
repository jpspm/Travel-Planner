package com.jpspm.travelplanner.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jpspm.travelplanner.resources.action_more
import com.jpspm.travelplanner.resources.backup_export_all
import com.jpspm.travelplanner.resources.backup_import
import com.jpspm.travelplanner.ui.backup.BackupDialogs
import com.jpspm.travelplanner.ui.backup.BackupReminderCard
import com.jpspm.travelplanner.ui.backup.BackupViewModel
import com.jpspm.travelplanner.ui.backup.rememberBackupImportLauncher
import com.jpspm.travelplanner.ui.reminders.AskNotificationPermissionOnce
import com.jpspm.travelplanner.ui.reminders.RemindersDialog
import com.jpspm.travelplanner.ui.reminders.RemindersViewModel
import com.jpspm.travelplanner.resources.reminders_title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.app_name
import com.jpspm.travelplanner.resources.today_next
import com.jpspm.travelplanner.resources.today_nothing_next
import com.jpspm.travelplanner.resources.today_open_trip
import com.jpspm.travelplanner.resources.today_tickets
import com.jpspm.travelplanner.resources.today_title
import com.jpspm.travelplanner.resources.trip_days
import com.jpspm.travelplanner.resources.trips_empty_message
import com.jpspm.travelplanner.resources.trips_empty_title
import com.jpspm.travelplanner.resources.trips_new
import com.jpspm.travelplanner.resources.trips_section_ongoing
import com.jpspm.travelplanner.resources.trips_section_past
import com.jpspm.travelplanner.resources.trips_section_upcoming
import com.jpspm.travelplanner.ui.components.AgendaItemRow
import com.jpspm.travelplanner.ui.components.EmptyState
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.emoji
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onNewTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenReservation: (String) -> Unit,
    onOpenAttachment: (String) -> Unit,
    viewModel: TripsViewModel = koinViewModel(),
    backupViewModel: BackupViewModel = koinViewModel(),
    remindersViewModel: RemindersViewModel = koinViewModel(),
) {
    var showReminders by remember { mutableStateOf(false) }
    AskNotificationPermissionOnce(remindersViewModel)
    if (showReminders) RemindersDialog(remindersViewModel, onDismiss = { showReminders = false })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showBackupReminder by backupViewModel.reminder.collectAsStateWithLifecycle()
    val importLauncher = rememberBackupImportLauncher(backupViewModel)
    var menuOpen by remember { mutableStateOf(false) }
    BackupDialogs(backupViewModel)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.backup_export_all)) },
                            onClick = { menuOpen = false; backupViewModel.export() },
                            enabled = !state.isEmpty,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.backup_import)) },
                            onClick = { menuOpen = false; importLauncher.launch() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.reminders_title)) },
                            onClick = { menuOpen = false; showReminders = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTrip,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.trips_new)) },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                emoji = "🧳",
                title = stringResource(Res.string.trips_empty_title),
                message = stringResource(Res.string.trips_empty_message),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (showBackupReminder) {
                item(key = "backup-reminder") {
                    BackupReminderCard(onBackup = { backupViewModel.export() }, onLater = { backupViewModel.snoozeReminder() })
                }
            }
            items(state.today, key = { "today:${it.trip.id}" }) { summary ->
                TodayCard(
                    summary = summary,
                    onOpenTrip = { onOpenTrip(summary.trip.id) },
                    onOpenTicket = { reservation ->
                        // Um anexo só: abre direto em tela cheia. Vários: abre a reserva para escolher.
                        val single = reservation.attachments.singleOrNull()
                        if (single != null) onOpenAttachment(single.id) else onOpenReservation(reservation.id)
                    },
                    onOpenItem = onOpenTrip,
                )
            }
            tripSection(Res.string.trips_section_ongoing, state.ongoing, onOpenTrip)
            tripSection(Res.string.trips_section_upcoming, state.upcoming, onOpenTrip)
            tripSection(Res.string.trips_section_past, state.past, onOpenTrip)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.tripSection(
    title: org.jetbrains.compose.resources.StringResource,
    trips: List<Trip>,
    onOpenTrip: (String) -> Unit,
) {
    if (trips.isEmpty()) return
    item(key = "header:${title.key}") { SectionHeader(stringResource(title), Modifier.padding(top = 8.dp)) }
    items(trips, key = { it.id }) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.id) }) }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            TripThumbnail(trip)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    TimeFormat.range(trip.startDate, trip.endDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(Res.plurals.trip_days, trip.days.size, trip.days.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TripThumbnail(trip: Trip) {
    val shape = RoundedCornerShape(12.dp)
    if (trip.coverPhotoPath != null) {
        AsyncImage(
            model = trip.coverPhotoPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(shape),
        )
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("🗺️", style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

@Composable
private fun TodayCard(
    summary: TodaySummary,
    onOpenTrip: () -> Unit,
    onOpenTicket: (Reservation) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(Res.string.today_title, TimeFormat.weekdayDayMonth(summary.date)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    summary.trip.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (summary.tickets.isNotEmpty()) {
                Text(
                    stringResource(Res.string.today_tickets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(summary.tickets, key = { it.id }) { reservation ->
                        AssistChip(
                            onClick = { onOpenTicket(reservation) },
                            label = { Text(reservation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Text(reservation.type.emoji) },
                            modifier = Modifier.height(48.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(Res.string.today_next),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            val next = summary.next
            if (next != null) {
                AgendaItemRow(item = next, zoneNames = summary.zoneNames, onClick = { onOpenItem(summary.trip.id) })
            } else {
                Text(
                    stringResource(Res.string.today_nothing_next),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Text(
                stringResource(Res.string.today_open_trip),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenTrip)
                    .padding(12.dp),
            )
        }
    }
}
