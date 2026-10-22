package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.domain.model.ReservationField
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlin.test.Test
import kotlin.test.assertEquals

class ReservationTypeTest {

    private fun details(vararg pairs: Pair<ReservationField, String>) = pairs.associate { it.first.key to it.second }

    @Test
    fun flightTitleFromFields() {
        val title = ReservationType.FLIGHT.buildTitle(
            "ignorado",
            details(
                ReservationField.AIRLINE to "LATAM",
                ReservationField.FLIGHT_NUMBER to "LA8084",
                ReservationField.ORIGIN to "GRU",
                ReservationField.DESTINATION to "FCO",
            ),
        )
        assertEquals("LATAM LA8084 · GRU → FCO", title)
    }

    @Test
    fun flightTitleWithPartialFields() {
        assertEquals("LA8084", ReservationType.FLIGHT.buildTitle("", details(ReservationField.FLIGHT_NUMBER to "LA8084")))
        assertEquals("GRU → FCO", ReservationType.FLIGHT.buildTitle("", details(ReservationField.ORIGIN to "GRU", ReservationField.DESTINATION to "FCO")))
    }

    @Test
    fun trainAndCarTitles() {
        assertEquals(
            "Trenitalia 9512 · Roma → Firenze",
            ReservationType.TRAIN_BUS.buildTitle(
                "",
                details(
                    ReservationField.COMPANY to "Trenitalia",
                    ReservationField.SERVICE_NUMBER to "9512",
                    ReservationField.ORIGIN to "Roma",
                    ReservationField.DESTINATION to "Firenze",
                ),
            ),
        )
        assertEquals(
            "Hertz · Grupo C",
            ReservationType.CAR_RENTAL.buildTitle("", details(ReservationField.COMPANY to "Hertz", ReservationField.CAR_CATEGORY to "Grupo C")),
        )
    }

    @Test
    fun typedTitleForNamedTypes() {
        assertEquals("Hotel Artemide", ReservationType.LODGING.buildTitle("  Hotel Artemide ", emptyMap()))
    }
}
