package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.reminders.ReminderKind
import com.jpspm.travelplanner.domain.reminders.ReminderPlanner
import com.jpspm.travelplanner.domain.reminders.ReminderSettings
import com.jpspm.travelplanner.domain.reminders.ReminderSource
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReminderPlannerTest {

    private val now = LocalDateTime(2027, 5, 1, 8, 0).toInstant(TimeZone.of("America/Sao_Paulo"))

    private fun flight(at: LocalDateTime, zone: String = "America/Sao_Paulo", isEnd: Boolean = false) =
        ReminderSource.OfReservation("voo", "Itália", "LATAM LA8084", ZonedLocal(at, zone), ReservationType.FLIGHT, isEnd)

    private fun hotel(at: LocalDateTime) =
        ReminderSource.OfReservation("hotel", "Itália", "Hotel Artemide", ZonedLocal(at, "Europe/Rome"), ReservationType.LODGING, false)

    private fun activity(at: LocalDateTime) = ReminderSource.OfActivity("museu", "Itália", "Museu", ZonedLocal(at, "Europe/Rome"))

    @Test
    fun defaultLeadTimesPerKind() {
        val reminders = ReminderPlanner.plan(
            listOf(
                flight(LocalDateTime(2027, 5, 1, 22, 0)),
                hotel(LocalDateTime(2027, 5, 2, 14, 0)),
                activity(LocalDateTime(2027, 5, 3, 10, 0)),
            ),
            ReminderSettings(),
            now,
        )
        assertEquals(listOf(ReminderKind.TRANSIT, ReminderKind.RESERVATION, ReminderKind.ACTIVITY), reminders.map { it.kind })
        // Voo às 22:00 em São Paulo: lembrete 3 h antes, às 19:00 de lá.
        assertEquals(LocalDateTime(2027, 5, 1, 22, 0).toInstant(TimeZone.of("America/Sao_Paulo")) - 3.hours, reminders[0].at)
        // Atividade em Roma às 10:00: 30 min antes no fuso de Roma.
        assertEquals(LocalDateTime(2027, 5, 3, 10, 0).toInstant(TimeZone.of("Europe/Rome")) - 30.minutes, reminders[2].at)
    }

    @Test
    fun skipsPastDisabledArrivalsAndFarFuture() {
        val reminders = ReminderPlanner.plan(
            listOf(
                flight(LocalDateTime(2027, 5, 1, 9, 0)), // lembrete seria às 06:00: já passou
                flight(LocalDateTime(2027, 5, 2, 6, 0), "Europe/Rome", isEnd = true), // chegada: sem lembrete
                activity(LocalDateTime(2027, 9, 1, 10, 0)), // além do horizonte de 45 dias
                hotel(LocalDateTime(2027, 5, 2, 14, 0)),
            ),
            ReminderSettings(reservationEnabled = false),
            now,
        )
        assertEquals(emptyList(), reminders)
    }

    @Test
    fun customLeadTimeAndStableKeys() {
        val settings = ReminderSettings(activityMinutes = 90)
        val first = ReminderPlanner.plan(listOf(activity(LocalDateTime(2027, 5, 3, 10, 0))), settings, now).single()
        assertEquals(90, first.minutesBefore)
        val again = ReminderPlanner.plan(listOf(activity(LocalDateTime(2027, 5, 3, 10, 0))), settings, now).single()
        assertEquals(first.key, again.key)
        val moved = ReminderPlanner.plan(listOf(activity(LocalDateTime(2027, 5, 3, 11, 0))), settings, now).single()
        assertEquals(false, first.key == moved.key, "horário mudou: outro lembrete")
    }
}
