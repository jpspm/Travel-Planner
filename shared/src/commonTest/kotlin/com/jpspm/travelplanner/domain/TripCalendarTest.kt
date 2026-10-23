package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.export.TripCalendar
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TripCalendarTest {

    private val d1 = LocalDate(2027, 5, 1)
    private val trip = Trip("t", "Itália, 2027", d1, LocalDate(2027, 5, 2), null, "", null, Instant.DISTANT_PAST, Instant.DISTANT_PAST)
    private val labels = TripCalendar.Labels(moment = { "Partida" }, type = { "Voo" })

    private fun build(): String {
        val activities = listOf(
            Activity("museu", "t", d1, LocalTime(10, 0), LocalTime(12, 30), "Europe/Rome", "Museu; com vírgula, e barra \\", "", null, 0),
            Activity("livre", "t", d1, null, null, "Europe/Rome", "Tarde livre", "", null, 1),
        )
        val flight = Reservation(
            "voo", "t", ReservationType.FLIGHT, "LATAM LA8084", null,
            ZonedLocal(LocalDateTime(2027, 5, 1, 22, 0), "America/Sao_Paulo"),
            ZonedLocal(LocalDateTime(2027, 5, 2, 14, 0), "Europe/Rome"),
            "XYZ987", 100_000, "", emptyMap(), emptyList(),
        )
        return TripCalendar.build(trip, AgendaBuilder.build(trip.days, activities, listOf(flight)), labels, Instant.parse("2026-10-23T12:00:00Z"))
    }

    @Test
    fun eventsInUtcWithFlightUntilArrival() {
        val ics = build()
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        // Museu 10:00 em Roma (UTC+2) = 08:00Z, até 12:30 = 10:30Z
        assertTrue("DTSTART:20270501T080000Z\r\nDTEND:20270501T103000Z" in ics)
        // Voo 22:00 em São Paulo (UTC-3) = 01:00Z do dia 2; chega 14:00 em Roma = 12:00Z
        assertTrue("DTSTART:20270502T010000Z\r\nDTEND:20270502T120000Z" in ics)
        assertTrue("SUMMARY:Partida: LATAM LA8084" in ics)
        // Atividade sem horário: dia inteiro
        assertTrue("DTSTART;VALUE=DATE:20270501\r\nDTEND;VALUE=DATE:20270502" in ics)
    }

    @Test
    fun escapesTextAndNeverLeaksCodesOrPrices() {
        val ics = build()
        assertTrue("SUMMARY:Museu\\; com vírgula\\, e barra \\\\" in ics)
        assertTrue("X-WR-CALNAME:Itália\\, 2027" in ics)
        assertFalse("XYZ987" in ics)
        assertFalse("R$" in ics || "1.000" in ics, "valor não entra")
    }

    @Test
    fun foldsLongLinesAt75Bytes() {
        val long = "DESCRIPTION:" + "á".repeat(80)
        val folded = TripCalendar.fold(long)
        folded.split("\r\n").forEach { assertTrue(it.encodeToByteArray().size <= 75, it) }
        assertEquals(long, folded.replace("\r\n ", ""))
    }
}
