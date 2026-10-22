package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.map.StaticMapSpecs
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.repository.MapScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StaticMapSpecsTest {

    private val d1 = LocalDate(2027, 5, 1)
    private val d2 = LocalDate(2027, 5, 2)
    private val d3 = LocalDate(2027, 5, 3)

    private fun place(id: String, lat: Double?, lon: Double?) =
        Place(id, "t", id, "", lat, lon, "IT", "Roma", "Europe/Rome", null, null, null, null, null)

    private fun activity(id: String, date: LocalDate, place: Place?, hour: Int) =
        Activity(id, "t", date, LocalTime(hour, 0), null, "Europe/Rome", id, "", place, 0)

    private val colosseo = place("colosseo", 41.8902, 12.4924)
    private val vaticano = place("vaticano", 41.9065, 12.4536)
    private val manual = place("manual", null, null)

    @Test
    fun tripImagePlusOneImagePerDayWithPlaces() {
        val days = AgendaBuilder.build(
            listOf(d1, d2, d3),
            listOf(
                activity("a", d1, vaticano, 9),
                activity("b", d1, colosseo, 14),
                activity("c", d2, manual, 10), // sem coordenadas: dia 2 não tem imagem
                activity("d", d3, colosseo, 10),
            ),
            emptyList(),
        )
        val specs = StaticMapSpecs.build(d1, days)
        assertEquals(listOf(MapScope.TRIP to null, MapScope.DAY to d1, MapScope.DAY to d3), specs.map { it.scope to it.date })
        assertEquals(listOf("1", "2"), specs[1].pins.map { it.badge }) // ordem do dia
        // Na viagem, o badge é o número do dia; o Coliseu aparece uma vez só.
        assertEquals(listOf("1", "1"), specs[0].pins.map { it.badge })
    }

    @Test
    fun signatureChangesWhenPlacesChange() {
        val before = StaticMapSpecs.build(d1, AgendaBuilder.build(listOf(d1), listOf(activity("a", d1, colosseo, 9)), emptyList()))
        val same = StaticMapSpecs.build(d1, AgendaBuilder.build(listOf(d1), listOf(activity("a", d1, colosseo, 9)), emptyList()))
        val after = StaticMapSpecs.build(d1, AgendaBuilder.build(listOf(d1), listOf(activity("a", d1, vaticano, 9)), emptyList()))
        assertEquals(before.map { it.signature }, same.map { it.signature })
        assertNotEquals(before.map { it.signature }, after.map { it.signature })
    }

    @Test
    fun noPlacesNoImages() {
        val days = AgendaBuilder.build(listOf(d1), listOf(activity("a", d1, manual, 9)), emptyList())
        assertEquals(emptyList(), StaticMapSpecs.build(d1, days))
    }
}
