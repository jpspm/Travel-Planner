package com.jpspm.travelplanner.domain.reminders

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Preferências de lembrete (RF-43). Antecedência em minutos; `enabled = false` desliga o tipo. */
data class ReminderSettings(
    val transitEnabled: Boolean = true,
    val transitMinutes: Int = 180,
    val reservationEnabled: Boolean = true,
    val reservationMinutes: Int = 60,
    val activityEnabled: Boolean = true,
    val activityMinutes: Int = 30,
)

/** Algo com horário que pode gerar lembrete. */
sealed interface ReminderSource {
    val id: String
    val tripName: String
    val title: String
    val moment: ZonedLocal

    data class OfActivity(override val id: String, override val tripName: String, override val title: String, override val moment: ZonedLocal) : ReminderSource

    /** [isEnd] = check-out ou devolução do carro. */
    data class OfReservation(
        override val id: String,
        override val tripName: String,
        override val title: String,
        override val moment: ZonedLocal,
        val type: ReservationType,
        val isEnd: Boolean,
    ) : ReminderSource
}

enum class ReminderKind { TRANSIT, RESERVATION, ACTIVITY }

/** Lembrete pronto para agendar. [key] é estável, para cancelar e reagendar sem duplicar. */
data class Reminder(
    val key: String,
    val at: Instant,
    val kind: ReminderKind,
    val tripName: String,
    val title: String,
    /** Horário do item, já com fuso, para o texto ("14:00 (Roma, UTC+2)"). */
    val moment: ZonedLocal,
    val minutesBefore: Int,
)

object ReminderPlanner {

    /** Só agenda os próximos dias, para não encher o sistema; o resto entra nas próximas sincronizações. */
    val HORIZON: Duration = 45.days

    private val TRANSIT = setOf(ReservationType.FLIGHT, ReservationType.TRAIN_BUS)

    /** Lembretes futuros dentro do [HORIZON], em ordem de horário. */
    fun plan(sources: List<ReminderSource>, settings: ReminderSettings, now: Instant): List<Reminder> =
        sources.mapNotNull { source ->
            val (kind, enabled, minutes) = when (source) {
                is ReminderSource.OfActivity -> Triple(ReminderKind.ACTIVITY, settings.activityEnabled, settings.activityMinutes)
                is ReminderSource.OfReservation -> when {
                    source.type in TRANSIT && !source.isEnd -> Triple(ReminderKind.TRANSIT, settings.transitEnabled, settings.transitMinutes)
                    // Chegada de voo/trem não gera lembrete: o viajante já está a bordo.
                    source.type in TRANSIT -> return@mapNotNull null
                    else -> Triple(ReminderKind.RESERVATION, settings.reservationEnabled, settings.reservationMinutes)
                }
            }
            if (!enabled) return@mapNotNull null
            val at = source.moment.toInstant() - minutes.minutes
            if (at <= now || at > now + HORIZON) return@mapNotNull null
            Reminder("${source.id}@${source.moment.dateTime}", at, kind, source.tripName, source.title, source.moment, minutes)
        }.sortedBy { it.at }
}
