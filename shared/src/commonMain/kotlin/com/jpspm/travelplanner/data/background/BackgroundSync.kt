package com.jpspm.travelplanner.data.background

import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.settings.SettingsStore
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.reminders.Reminder
import com.jpspm.travelplanner.domain.reminders.ReminderPlanner
import com.jpspm.travelplanner.domain.reminders.ReminderSource
import com.jpspm.travelplanner.platform.HomeWidgetUpdater
import com.jpspm.travelplanner.platform.ReminderScheduler
import com.jpspm.travelplanner.platform.ScheduledNotification
import com.jpspm.travelplanner.platform.SharedFile
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.reminder_text
import com.jpspm.travelplanner.resources.reminder_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

/**
 * Mantém lembretes (RF-43) e widget (RF-45) em dia: a cada mudança no roteiro ou nas preferências,
 * recalcula os lembretes dos próximos dias e substitui os agendados no sistema. Roda enquanto o
 * processo do app existir (abrir o app ou reiniciar o celular inicia de novo).
 */
@OptIn(FlowPreview::class)
class BackgroundSync(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val scheduler: ReminderScheduler,
    private val widget: HomeWidgetUpdater,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            combine(
                db.tripDao().observeAll(),
                db.activityDao().observeTimed(),
                db.reservationDao().observeDated(),
                settings.settings,
            ) { trips, activities, reservations, appSettings ->
                val tripNames = trips.associate { it.id to it.name }
                val sources = buildList {
                    activities.forEach { a ->
                        val start = a.startTime ?: return@forEach
                        add(ReminderSource.OfActivity("a:${a.id}", tripNames[a.tripId].orEmpty(), a.title,
                            ZonedLocal(LocalDate.parse(a.date).atTime(LocalTime.parse(start)), a.timeZoneId)))
                    }
                    reservations.forEach { r ->
                        val type = runCatching { ReservationType.valueOf(r.type) }.getOrDefault(ReservationType.OTHER)
                        val trip = tripNames[r.tripId].orEmpty()
                        if (r.startDateTime != null && r.startTimeZoneId != null) {
                            add(ReminderSource.OfReservation("r:${r.id}", trip, r.title, ZonedLocal(LocalDateTime.parse(r.startDateTime), r.startTimeZoneId), type, isEnd = false))
                        }
                        // Check-out e devolução do carro também lembram; chegada de voo/trem não.
                        if (type.endIsSeparateAgendaItem && r.endDateTime != null && r.endTimeZoneId != null) {
                            add(ReminderSource.OfReservation("r:${r.id}:end", trip, r.title, ZonedLocal(LocalDateTime.parse(r.endDateTime), r.endTimeZoneId), type, isEnd = true))
                        }
                    }
                }
                ReminderPlanner.plan(sources, appSettings.reminders, clock.now())
            }
                .debounce(1_000)
                .collect { reminders ->
                    scheduler.replaceAll(reminders.map { it.toNotification() })
                    widget.update()
                }
        }
    }

    private suspend fun Reminder.toNotification() = ScheduledNotification(
        key = key,
        at = at,
        title = getString(Res.string.reminder_title, leadText(minutesBefore), title),
        text = getString(Res.string.reminder_text, TimeFormat.timeWithZone(moment), tripName),
    )

    private fun leadText(minutes: Int): String = when {
        minutes % 1440 == 0 -> "${minutes / 1440} d"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** Arquivos recebidos pelo "Compartilhar" do sistema, aguardando o usuário escolher a reserva (RF-44). */
class IncomingShare {
    private val state = MutableStateFlow<List<SharedFile>>(emptyList())
    val files: StateFlow<List<SharedFile>> = state

    fun receive(files: List<SharedFile>) {
        if (files.isNotEmpty()) state.value = files
    }

    fun clear() {
        state.value = emptyList()
    }
}
