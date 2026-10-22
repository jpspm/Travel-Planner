package com.jpspm.travelplanner.core

import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.core.time.ZonedLocal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeFormatTest {

    @Test
    fun utcOffsetLabels() {
        assertEquals("UTC", TimeFormat.utcOffset(UtcOffset(hours = 0)))
        assertEquals("UTC+1", TimeFormat.utcOffset(UtcOffset(hours = 1)))
        assertEquals("UTC-3", TimeFormat.utcOffset(UtcOffset(hours = -3)))
        assertEquals("UTC+5:30", TimeFormat.utcOffset(UtcOffset(hours = 5, minutes = 30)))
        assertEquals("UTC-9:30", TimeFormat.utcOffset(UtcOffset(hours = -9, minutes = -30)))
    }

    @Test
    fun timeWithZoneUsesPlaceNameAndOffsetOfThatDate() {
        // Roma em janeiro: UTC+1 (horário padrão)
        val winter = ZonedLocal(LocalDateTime(2027, 1, 15, 14, 0), "Europe/Rome")
        assertEquals("14:00 (Roma, UTC+1)", TimeFormat.timeWithZone(winter, "Roma"))
        // Roma em julho: UTC+2 (horário de verão)
        val summer = ZonedLocal(LocalDateTime(2027, 7, 15, 14, 0), "Europe/Rome")
        assertEquals("14:00 (Roma, UTC+2)", TimeFormat.timeWithZone(summer, "Roma"))
    }

    @Test
    fun timeWithZoneFallsBackToZoneCity() {
        val moment = ZonedLocal(LocalDateTime(2027, 3, 1, 9, 5), "America/Sao_Paulo")
        assertEquals("09:05 (Sao Paulo, UTC-3)", TimeFormat.timeWithZone(moment))
    }

    @Test
    fun dateFormats() {
        val date = LocalDate(2026, 10, 22)
        assertEquals("22/10/2026", TimeFormat.date(date))
        assertEquals("22 out", TimeFormat.dayMonth(date))
        assertEquals("qui, 22 out", TimeFormat.weekdayDayMonth(date))
    }

    @Test
    fun ranges() {
        assertEquals("22 – 30 out 2026", TimeFormat.range(LocalDate(2026, 10, 22), LocalDate(2026, 10, 30)))
        assertEquals("22 out – 3 nov 2026", TimeFormat.range(LocalDate(2026, 10, 22), LocalDate(2026, 11, 3)))
        assertEquals("28 dez 2026 – 5 jan 2027", TimeFormat.range(LocalDate(2026, 12, 28), LocalDate(2027, 1, 5)))
        assertEquals("22 out 2026", TimeFormat.range(LocalDate(2026, 10, 22), LocalDate(2026, 10, 22)))
    }

    @Test
    fun instantComparesAcrossZones() {
        // 10:00 em São Paulo (UTC-3) e 14:00 em Roma no inverno (UTC+1) são o mesmo instante: 13:00 UTC.
        val sp = ZonedLocal(LocalDateTime(2027, 1, 15, 10, 0), "America/Sao_Paulo")
        val rome = ZonedLocal(LocalDateTime(2027, 1, 15, 14, 0), "Europe/Rome")
        assertEquals(sp.toInstant(), rome.toInstant())
        // 13:30 em Roma acontece antes das 10:00 em São Paulo.
        val earlierRome = ZonedLocal(LocalDateTime(2027, 1, 15, 13, 30), "Europe/Rome")
        assertTrue(earlierRome.toInstant() < sp.toInstant())
    }

    @Test
    fun dstGapIsShiftedForward() {
        // Em 28/03/2027 os relógios de Roma pulam de 02:00 para 03:00; 02:30 não existe.
        val gap = ZonedLocal(LocalDateTime(2027, 3, 28, 2, 30), "Europe/Rome")
        val after = ZonedLocal(LocalDateTime(2027, 3, 28, 3, 30), "Europe/Rome")
        assertEquals(after.toInstant(), gap.toInstant())
    }
}
