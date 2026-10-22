package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.city.CityGrouper
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CityGrouperTest {

    private val d1 = LocalDate(2027, 5, 1)
    private val d2 = LocalDate(2027, 5, 2)
    private val d3 = LocalDate(2027, 5, 3)
    private val d4 = LocalDate(2027, 5, 4)
    private val days = listOf(d1, d2, d3, d4)

    private fun place(id: String, lat: Double?, lon: Double?, city: String?, photo: String? = null, address: String = "") =
        Place(id, "t", id, address, lat, lon, "IT", city, "Europe/Rome", null, photo, null, null, null)

    private fun activity(id: String, date: LocalDate, place: Place?, time: LocalTime? = LocalTime(10, 0)) =
        Activity(id, "t", date, time, null, "Europe/Rome", id, "", place, 0)

    private fun hotel(id: String, place: Place, from: LocalDate, to: LocalDate) = Reservation(
        id, "t", ReservationType.LODGING, id, place,
        ZonedLocal(from.atTime(14, 0), "Europe/Rome"), ZonedLocal(to.atTime(11, 0), "Europe/Rome"),
        "", null, "", emptyMap(), emptyList(),
    )

    private fun LocalDate.atTime(h: Int, m: Int) = LocalDateTime(year, month, day, h, m)

    private val colosseo = place("Colosseo", 41.8902, 12.4924, "Roma", photo = "colosseo.jpg")
    private val vaticano = place("Musei Vaticani", 41.9065, 12.4536, "Città del Vaticano")
    private val uffizi = place("Uffizi", 43.7678, 11.2553, "Firenze")

    @Test
    fun nearbyPlacesFormOneDetectedCity() {
        val agenda = AgendaBuilder.build(days, listOf(activity("a", d1, colosseo), activity("b", d2, vaticano)), emptyList())
        val cities = CityGrouper.group(emptyList(), agenda)
        val rome = cities.single()
        assertEquals("Roma", rome.name) // o Vaticano fica a ~4 km e entra em Roma
        assertEquals(d1, rome.startDate)
        assertEquals(d2, rome.endDate)
        assertEquals(2, rome.activityCount)
        assertEquals("colosseo.jpg", rome.photoPath)
        assertTrue(!rome.isManual)
    }

    @Test
    fun distantPlacesFormSeparateCitiesInDateOrder() {
        val agenda = AgendaBuilder.build(
            days,
            listOf(activity("f", d3, uffizi), activity("r", d1, colosseo)),
            emptyList(),
        )
        assertEquals(listOf("Roma", "Firenze"), CityGrouper.group(emptyList(), agenda).map { it.name })
    }

    @Test
    fun manualDestinationAttractsNearbyPlacesAndKeepsItsDates() {
        val dest = Destination("d", "t", "Roma", "Itália", place("Roma", 41.9028, 12.4964, "Roma"), d1, d3, "Europe/Rome", 0)
        val agenda = AgendaBuilder.build(days, listOf(activity("a", d2, colosseo)), emptyList())
        val city = CityGrouper.group(listOf(dest), agenda).single()
        assertEquals("d:d", city.key)
        assertEquals(d1, city.startDate)
        assertEquals(d3, city.endDate)
        assertEquals(1, city.activityCount)
    }

    @Test
    fun manualDestinationWithoutCoordinatesMatchesByCityName() {
        val dest = Destination("d", "t", "roma", "", null, d1, d2, "Europe/Rome", 0)
        val agenda = AgendaBuilder.build(days, listOf(activity("a", d1, colosseo)), emptyList())
        assertEquals("d:d", CityGrouper.group(listOf(dest), agenda).single().key)
    }

    @Test
    fun unlocatedItemsJoinTheOnlyCityCoveringTheirDate() {
        val agenda = AgendaBuilder.build(
            days,
            listOf(
                activity("r", d1, colosseo),
                activity("r2", d2, colosseo),
                activity("f", d4, uffizi),
                activity("livre-roma", d2, null, time = null),
                activity("viagem", d3, null), // nenhuma cidade cobre o dia 3
            ),
            emptyList(),
        )
        val (rome, florence) = CityGrouper.group(emptyList(), agenda)
        assertEquals(3, rome.activityCount)
        assertEquals(1, florence.activityCount)
    }

    @Test
    fun lodgingIsListedAndItsPhotoPreferredOverOtherPlaces() {
        val hotelPlace = place("Hotel Artemide", 41.9009, 12.4936, "Roma", photo = "hotel.jpg")
        val agenda = AgendaBuilder.build(days, listOf(activity("a", d1, colosseo)), listOf(hotel("h", hotelPlace, d1, d3)))
        val city = CityGrouper.group(emptyList(), agenda).single()
        assertEquals(listOf("h"), city.lodgings.map { it.id })
        assertEquals("hotel.jpg", city.photoPath)
        assertEquals(1, city.reservationCount) // check-in e check-out contam como uma reserva
    }

    @Test
    fun zoneNamesAreOmittedWhenAmbiguous() {
        val agenda = AgendaBuilder.build(days, listOf(activity("r", d1, colosseo), activity("f", d3, uffizi)), emptyList())
        val cities = CityGrouper.group(emptyList(), agenda)
        assertEquals(emptyMap(), CityGrouper.zoneNames(cities)) // Roma e Firenze no mesmo fuso
        assertEquals(mapOf("Europe/Rome" to "Roma"), CityGrouper.zoneNames(cities.take(1)))
    }

    @Test
    fun groupingDoesNotDependOnItemOrder() {
        // O Vaticano (outra "cidade" a 4 km) vem antes do Coliseu no roteiro.
        val dest = Destination("d", "t", "Roma", "Itália", null, d1, d4, "Europe/Rome", 0)
        val agenda = AgendaBuilder.build(
            days,
            listOf(activity("v", d1, vaticano, LocalTime(9, 0)), activity("c", d1, colosseo, LocalTime(11, 0))),
            emptyList(),
        )
        val city = CityGrouper.group(listOf(dest), agenda).single()
        assertEquals("Roma", city.name)
        assertEquals(2, city.activityCount)
        assertEquals(mapOf("Europe/Rome" to "Roma"), CityGrouper.zoneNames(listOf(city)))
    }

    @Test
    fun detectedCityIsNamedAfterMostCommonCity() {
        val agenda = AgendaBuilder.build(
            days,
            listOf(activity("v", d1, vaticano, LocalTime(9, 0)), activity("c", d1, colosseo, LocalTime(11, 0)), activity("c2", d2, colosseo)),
            emptyList(),
        )
        assertEquals("Roma", CityGrouper.group(emptyList(), agenda).single().name)
    }

    @Test
    fun cityFromAddressFallback() {
        assertEquals("Roma", CityGrouper.cityFromAddress("Piazza del Colosseo, Celio, Roma, Lácio, Itália"))
        assertEquals(null, CityGrouper.cityFromAddress("Itália"))
    }
}
