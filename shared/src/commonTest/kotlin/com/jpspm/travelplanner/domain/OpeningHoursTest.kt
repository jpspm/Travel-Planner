package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.domain.places.OpeningHours
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpeningHoursTest {

    private fun open(text: String, day: DayOfWeek, h: Int, m: Int = 0) = assertNotNull(OpeningHours.parse(text)).isOpenAt(day, LocalTime(h, m))

    @Test
    fun weekdaysWithSundayOff() {
        // Formato real de museus no OSM
        val text = "Mo-Fr 09:00-18:00; Sa 10:00-14:00; Su off"
        assertTrue(open(text, MONDAY, 9))
        assertTrue(open(text, FRIDAY, 17, 59))
        assertFalse(open(text, FRIDAY, 18), "fecha às 18:00")
        assertFalse(open(text, SATURDAY, 15))
        assertTrue(assertNotNull(OpeningHours.parse(text)).isClosedAllDay(SUNDAY))
    }

    @Test
    fun splitShiftsAndDayLists() {
        val text = "Mo,We,Fr 09:00-12:00,14:00-18:00"
        assertTrue(open(text, MONDAY, 11))
        assertFalse(open(text, MONDAY, 13), "pausa do almoço")
        assertTrue(open(text, MONDAY, 15))
        assertTrue(assertNotNull(OpeningHours.parse(text)).isClosedAllDay(TUESDAY))
    }

    @Test
    fun laterRuleOverridesEarlierForItsDays() {
        // Coliseu: todo dia 08:30-19:00, mas segunda fecha mais cedo.
        val text = "08:30-19:00; Mo 08:30-14:00"
        assertTrue(open(text, TUESDAY, 18))
        assertFalse(open(text, MONDAY, 18))
        assertTrue(open(text, MONDAY, 13))
    }

    @Test
    fun overMidnightSpillsIntoNextDay() {
        val text = "Fr-Sa 18:00-02:00"
        assertTrue(open(text, FRIDAY, 23))
        assertTrue(open(text, SATURDAY, 1), "madrugada de sábado, do turno de sexta")
        assertFalse(open(text, SATURDAY, 3))
        assertTrue(open(text, SUNDAY, 1), "madrugada de domingo, do turno de sábado")
        assertFalse(open(text, FRIDAY, 1))
    }

    @Test
    fun allDayAndWrapAroundRanges() {
        assertTrue(open("24/7", SUNDAY, 3))
        assertTrue(open("Fr-Mo 10:00-16:00", SUNDAY, 12))
        assertFalse(open("Fr-Mo 10:00-16:00", TUESDAY, 12))
        assertTrue(open("Sa,Su", SUNDAY, 23), "dia sem horário = aberto o dia todo")
    }

    @Test
    fun unsupportedFormatsReturnNull() {
        assertNull(OpeningHours.parse("Mo-Fr 09:00-17:00; PH off"))
        assertNull(OpeningHours.parse("Apr-Oct: 09:00-19:00"))
        assertNull(OpeningHours.parse("sunrise-sunset"))
        assertNull(OpeningHours.parse("Mo-Fr 09:00+"))
        assertNull(OpeningHours.parse(""))
        assertNull(OpeningHours.parse(null))
    }

    @Test
    fun describeInPortuguese() {
        assertEquals("seg–sex 09:00–18:00 · dom fechado", OpeningHours.describe("Mo-Fr 09:00-18:00; Su off"))
        assertEquals("24 horas", OpeningHours.describe("24/7"))
    }
}
