package com.jpspm.travelplanner.core

import com.jpspm.travelplanner.core.time.TimeZoneSuggester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeZoneSuggesterTest {

    @Test
    fun singleZoneCountry() {
        assertEquals("Europe/Rome", TimeZoneSuggester.suggest("IT", 41.89, 12.49))
        assertEquals("Europe/Paris", TimeZoneSuggester.suggest("fr", null, null))
    }

    @Test
    fun brazilUsesNearestZone() {
        assertEquals("America/Sao_Paulo", TimeZoneSuggester.suggest("BR", -23.56, -46.66)) // MASP
        assertEquals("America/Recife", TimeZoneSuggester.suggest("BR", -8.06, -34.88)) // Recife
        assertEquals("America/Manaus", TimeZoneSuggester.suggest("BR", -3.10, -60.02)) // Manaus
    }

    @Test
    fun usaUsesNearestZone() {
        assertEquals("America/New_York", TimeZoneSuggester.suggest("US", 40.75, -73.98))
        assertEquals("America/Los_Angeles", TimeZoneSuggester.suggest("US", 34.05, -118.24))
    }

    @Test
    fun unknownCountryFallsBackToCoordinates() {
        assertEquals("Europe/Rome", TimeZoneSuggester.suggest(null, 41.9, 12.5))
        assertNull(TimeZoneSuggester.suggest(null, null, null))
    }

    @Test
    fun zonesOfCountry() {
        val br = TimeZoneSuggester.zonesOf("BR")
        assertTrue("America/Sao_Paulo" in br && "America/Noronha" in br && br.size > 10)
    }

    @Test
    fun iso6709() {
        val (lat, lon) = TimeZoneSuggester.parseIso6709("-2332-04637")!!
        assertEquals(-23.533, lat, 0.01)
        assertEquals(-46.617, lon, 0.01)
        val (lat2, lon2) = TimeZoneSuggester.parseIso6709("+404251-0740023")!!
        assertEquals(40.714, lat2, 0.01)
        assertEquals(-74.006, lon2, 0.01)
    }
}
