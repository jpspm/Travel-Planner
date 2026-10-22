package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import com.jpspm.travelplanner.domain.model.TripStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AgendaBuilderTest {

    private val day1 = LocalDate(2027, 1, 15)
    private val day2 = LocalDate(2027, 1, 16)

    private fun activity(id: String, date: LocalDate, time: LocalTime?, zone: String = "Europe/Rome", order: Int = 0) =
        Activity(id, "t", date, time, null, zone, id, "", null, order)

    private fun reservation(
        id: String,
        type: ReservationType,
        start: ZonedLocal?,
        end: ZonedLocal? = null,
    ) = Reservation(id, "t", type, id, null, start, end, "", null, "", emptyMap(), emptyList())

    private fun AgendaItem.id(): String = when (this) {
        is AgendaItem.ActivityItem -> activity.id
        is AgendaItem.ReservationItem -> "${reservation.id}:$role"
    }

    @Test
    fun untimedFirstInManualOrderThenTimedByInstant() {
        val items = AgendaBuilder.build(
            tripDays = listOf(day1),
            activities = listOf(
                activity("museu", day1, LocalTime(15, 0)),
                activity("livre-b", day1, null, order = 2),
                activity("cafe", day1, LocalTime(9, 0)),
                activity("livre-a", day1, null, order = 1),
            ),
            reservations = emptyList(),
        ).single().items
        assertEquals(listOf("livre-a", "livre-b", "cafe", "museu"), items.map { it.id() })
    }

    @Test
    fun timedItemsInDifferentZonesAreOrderedByRealInstant() {
        // Voo sai de São Paulo 10:00 (13:00 UTC); atividade em Roma 13:30 (12:30 UTC) acontece antes.
        val flight = reservation(
            "voo", ReservationType.FLIGHT,
            start = ZonedLocal(LocalDateTime(2027, 1, 15, 10, 0), "America/Sao_Paulo"),
            end = ZonedLocal(LocalDateTime(2027, 1, 16, 2, 0), "Europe/Rome"),
        )
        val items = AgendaBuilder.build(
            listOf(day1),
            listOf(activity("roma", day1, LocalTime(13, 30))),
            listOf(flight),
        ).single().items
        assertEquals(listOf("roma", "voo:START"), items.map { it.id() })
    }

    @Test
    fun lodgingShowsCheckInAndCheckOutOnTheirDays() {
        val hotel = reservation(
            "hotel", ReservationType.LODGING,
            start = ZonedLocal(LocalDateTime(2027, 1, 15, 14, 0), "Europe/Rome"),
            end = ZonedLocal(LocalDateTime(2027, 1, 16, 12, 0), "Europe/Rome"),
        )
        val days = AgendaBuilder.build(listOf(day1, day2), emptyList(), listOf(hotel))
        assertEquals(listOf("hotel:START"), days[0].items.map { it.id() })
        assertEquals(listOf("hotel:END"), days[1].items.map { it.id() })
    }

    @Test
    fun flightArrivalIsNotASeparateItem() {
        val flight = reservation(
            "voo", ReservationType.FLIGHT,
            start = ZonedLocal(LocalDateTime(2027, 1, 15, 22, 0), "America/Sao_Paulo"),
            end = ZonedLocal(LocalDateTime(2027, 1, 16, 14, 0), "Europe/Rome"),
        )
        val days = AgendaBuilder.build(listOf(day1, day2), emptyList(), listOf(flight))
        assertEquals(listOf("voo:START"), days[0].items.map { it.id() })
        assertEquals(emptyList(), days[1].items)
    }

    @Test
    fun includesEmptyTripDaysAndDaysOutsideTrip() {
        val eve = LocalDate(2027, 1, 14)
        val flight = reservation("voo", ReservationType.FLIGHT, ZonedLocal(LocalDateTime(2027, 1, 14, 23, 0), "America/Sao_Paulo"))
        val days = AgendaBuilder.build(listOf(day1, day2), emptyList(), listOf(flight))
        assertEquals(listOf(eve, day1, day2), days.map { it.date })
    }

    @Test
    fun reservationsWithoutDateStayOutOfItinerary() {
        val days = AgendaBuilder.build(listOf(day1), emptyList(), listOf(reservation("x", ReservationType.OTHER, null)))
        assertEquals(emptyList(), days.single().items)
    }

    @Test
    fun nextItemIsFirstTimedItemNotStartedYet() {
        val items = AgendaBuilder.build(
            listOf(day1),
            listOf(
                activity("cafe", day1, LocalTime(9, 0)),
                activity("museu", day1, LocalTime(15, 0)),
                activity("livre", day1, null),
            ),
            emptyList(),
        ).single().items
        val noon: Instant = LocalDateTime(2027, 1, 15, 12, 0).toInstant(TimeZone.of("Europe/Rome"))
        assertEquals("museu", AgendaBuilder.nextItem(items, noon)?.id())
        val night: Instant = LocalDateTime(2027, 1, 15, 22, 0).toInstant(TimeZone.of("Europe/Rome"))
        assertNull(AgendaBuilder.nextItem(items, night))
    }

    @Test
    fun tripStatusAndDays() {
        val trip = Trip("t", "Itália", day1, LocalDate(2027, 1, 20), null, "", null, Instant.DISTANT_PAST, Instant.DISTANT_PAST)
        assertEquals(TripStatus.UPCOMING, trip.status(LocalDate(2027, 1, 14)))
        assertEquals(TripStatus.ONGOING, trip.status(day1))
        assertEquals(TripStatus.ONGOING, trip.status(LocalDate(2027, 1, 20)))
        assertEquals(TripStatus.PAST, trip.status(LocalDate(2027, 1, 21)))
        assertEquals(6, trip.days.size)
    }
}
