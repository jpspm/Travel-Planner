package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.planning.Conflict
import com.jpspm.travelplanner.domain.planning.ConflictDetector
import com.jpspm.travelplanner.domain.planning.DayOrder
import com.jpspm.travelplanner.domain.planning.WalkEstimate
import com.jpspm.travelplanner.domain.repository.LatLng
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlanningTest {

    private val d1 = LocalDate(2027, 5, 1)
    private val d2 = LocalDate(2027, 5, 2)
    private val d3 = LocalDate(2027, 5, 3)
    private val trip = Trip("t", "Roma", d1, d3, null, "", null, Instant.DISTANT_PAST, Instant.DISTANT_PAST)
    private val rome = "Europe/Rome"

    private fun place(id: String, lat: Double, lon: Double) =
        Place(id, "t", id, "", lat, lon, "IT", "Roma", rome, null, null, null, null, null)

    private fun activity(id: String, date: LocalDate, start: LocalTime? = null, end: LocalTime? = null, place: Place? = null, order: Int = 0) =
        Activity(id, "t", date, start, end, rome, id, "", place, order)

    private fun reservation(id: String, type: ReservationType, start: LocalDateTime, end: LocalDateTime? = null, place: Place? = null) =
        Reservation(id, "t", type, id, place, ZonedLocal(start, rome), end?.let { ZonedLocal(it, rome) }, "", null, "", emptyMap(), emptyList())

    // --- RF-38: distância e tempo a pé

    @Test
    fun walkEstimateUsesStreetFactorAndWalkingSpeed() {
        val colosseo = LatLng(41.8902, 12.4924)
        val trevi = LatLng(41.9009, 12.4833)
        val estimate = WalkEstimate.between(colosseo, trevi)!!
        // ~1,4 km em linha reta × 1,3 ≈ 1,8 km ≈ 22 min
        assertTrue(estimate.distanceKm in 1.7..1.95, "${estimate.distanceKm}")
        assertTrue(estimate.minutes in 20..24, "${estimate.minutes}")
        assertNull(WalkEstimate.between(colosseo, LatLng(41.89021, 12.49241)), "mesmo lugar")
    }

    @Test
    fun walkFormatting() {
        assertEquals("850 m", WalkEstimate.formatDistance(0.853))
        assertEquals("1,2 km", WalkEstimate.formatDistance(1.24))
        assertEquals("12 km", WalkEstimate.formatDistance(12.4))
        assertEquals("18 min", WalkEstimate.formatMinutes(18))
        assertEquals("1 h 05 min", WalkEstimate.formatMinutes(65))
    }

    // --- RF-39: sugerir ordem do dia

    @Test
    fun suggestsNearestNeighbourOrderFromLodging() {
        val hotel = place("hotel", 41.9000, 12.4900)
        val near = place("perto", 41.9010, 12.4910)
        val mid = place("meio", 41.9100, 12.5000)
        val far = place("longe", 41.9300, 12.5200)
        val day = AgendaBuilder.build(
            listOf(d2),
            listOf(
                activity("longe", d2, place = far, order = 0),
                activity("perto", d2, place = near, order = 1),
                activity("meio", d2, place = mid, order = 2),
                activity("sem-local", d2, order = 3),
                activity("com-horario", d2, LocalTime(20, 0), place = far),
            ),
            listOf(reservation("h", ReservationType.LODGING, LocalDateTime(2027, 5, 1, 14, 0), LocalDateTime(2027, 5, 3, 11, 0), hotel)),
        ).first { it.date == d2 }
        val suggestion = assertNotNull(DayOrder.suggest(day, listOf(reservation("h", ReservationType.LODGING, LocalDateTime(2027, 5, 1, 14, 0), LocalDateTime(2027, 5, 3, 11, 0), hotel))))
        assertEquals(listOf("perto", "meio", "longe", "sem-local"), suggestion.activityIds)
        assertTrue(suggestion.savedKm > 0.3)
    }

    @Test
    fun noSuggestionWhenAlreadyGoodOrTooFewActivities() {
        val a = place("a", 41.90, 12.49)
        val b = place("b", 41.91, 12.50)
        val c = place("c", 41.92, 12.51)
        val ordered = AgendaBuilder.build(listOf(d1), listOf(activity("a", d1, place = a, order = 0), activity("b", d1, place = b, order = 1), activity("c", d1, place = c, order = 2)), emptyList()).single()
        assertNull(DayOrder.suggest(ordered, emptyList()))
        val two = AgendaBuilder.build(listOf(d1), listOf(activity("a", d1, place = a), activity("c", d1, place = c, order = 1)), emptyList()).single()
        assertNull(DayOrder.suggest(two, emptyList()))
    }

    // --- RF-37: conflitos

    private fun detect(activities: List<Activity>, reservations: List<Reservation>): List<Conflict> =
        ConflictDetector.detect(trip, AgendaBuilder.build(trip.days, activities, reservations), activities, reservations)

    @Test
    fun overlappingActivities() {
        val conflicts = detect(
            listOf(
                activity("museu", d1, LocalTime(10, 0), LocalTime(12, 0)),
                activity("almoco", d1, LocalTime(11, 30), LocalTime(13, 0)),
                activity("tarde", d1, LocalTime(13, 0), LocalTime(15, 0)), // começa quando o almoço termina: ok
            ),
            emptyList(),
        )
        assertEquals(listOf(Conflict.Overlap(d1, "a:museu", "a:almoco")), conflicts)
    }

    @Test
    fun activityDuringFlight() {
        val conflicts = detect(
            listOf(activity("jantar", d1, LocalTime(21, 0)), activity("antes", d1, LocalTime(8, 0))),
            listOf(reservation("voo", ReservationType.FLIGHT, LocalDateTime(2027, 5, 1, 18, 0), LocalDateTime(2027, 5, 1, 23, 0))),
        )
        assertEquals(listOf(Conflict.DuringTransit(d1, "a:jantar", "r:voo")), conflicts)
    }

    @Test
    fun nightsWithoutLodgingOnlyWhenTheTripTracksLodging() {
        assertEquals(emptyList(), detect(emptyList(), emptyList()))
        val oneNight = reservation("hotel", ReservationType.LODGING, LocalDateTime(2027, 5, 1, 14, 0), LocalDateTime(2027, 5, 2, 11, 0))
        // Viagem de 1 a 3: noites de 1 e 2. Só a de 1 tem hotel.
        assertEquals(listOf(Conflict.NightWithoutLodging(d2)), detect(emptyList(), listOf(oneNight)))
        // Voo noturno cobre a noite de 2.
        val overnight = reservation("voo", ReservationType.FLIGHT, LocalDateTime(2027, 5, 2, 22, 0), LocalDateTime(2027, 5, 3, 7, 0))
        assertEquals(emptyList(), detect(emptyList(), listOf(oneNight, overnight)))
    }

    @Test
    fun activityOutsideTrip() {
        val conflicts = detect(listOf(activity("antes", LocalDate(2027, 4, 30), LocalTime(10, 0))), emptyList())
        assertEquals(listOf(Conflict.OutsideTrip(LocalDate(2027, 4, 30), "antes")), conflicts)
    }

    @Test
    fun closedPlaceWarnings() {
        // 1º de maio de 2027 é um sábado.
        val museum = place("museu", 41.9, 12.45).copy(openingHours = "Mo-Fr 09:00-18:00; Sa 09:00-13:00; Su off")
        val conflicts = detect(
            listOf(
                activity("tarde", d1, LocalTime(15, 0), place = museum), // sábado à tarde: fechado
                activity("manha", d1, LocalTime(10, 0), place = museum), // sábado de manhã: aberto
                activity("domingo", d2, place = museum), // domingo sem horário: fechado o dia todo
            ),
            emptyList(),
        )
        assertEquals(
            listOf(
                Conflict.ClosedAtTime(d1, "a:tarde", museum.openingHours!!),
                Conflict.ClosedAllDay(d2, "a:domingo", museum.openingHours!!),
            ),
            conflicts,
        )
        // Formato não entendido: nenhum aviso.
        val unknown = place("x", 41.9, 12.45).copy(openingHours = "Mo-Fr 09:00-18:00; PH off")
        assertEquals(emptyList(), detect(listOf(activity("y", d1, LocalTime(23, 0), place = unknown)), emptyList()))
    }

    @Test
    fun untimedActivitiesNeverConflict() {
        assertEquals(emptyList(), detect(listOf(activity("livre", d1), activity("livre2", d1)), emptyList()))
    }
}
