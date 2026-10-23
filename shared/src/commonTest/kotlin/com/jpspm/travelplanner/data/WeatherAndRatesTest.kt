package com.jpspm.travelplanner.data

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.core.money.MoneyFormat
import com.jpspm.travelplanner.data.remote.FrankfurterClient
import com.jpspm.travelplanner.data.remote.OpenMeteoClient
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.weather.DayForecast
import com.jpspm.travelplanner.domain.weather.WeatherCodes
import com.jpspm.travelplanner.domain.weather.WeatherPlanner
import com.jpspm.travelplanner.domain.weather.forecastKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherAndRatesTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun parsesOpenMeteoDailyForecast() {
        val body = """
            {"latitude":41.9,"longitude":12.5,"daily":{
              "time":["2026-10-24","2026-10-25"],
              "weather_code":[0,61],
              "temperature_2m_max":[24.4,19.6],
              "temperature_2m_min":[15.1,null]
            }}
        """.trimIndent()
        val days = OpenMeteoClient.parseOpenMeteo(body, LatLng(41.9, 12.5))
        // O segundo dia não tem mínima: fica de fora.
        val day = days.single()
        assertEquals(LocalDate(2026, 10, 24), day.date)
        assertEquals("☀️ 24° / 15°", day.short)
        assertEquals("Chuva", WeatherCodes.description(61))
    }

    @Test
    fun openMeteoRequestUsesDateRange() = runTest {
        var url = ""
        val client = HttpClient(MockEngine { request ->
            url = request.url.toString()
            respond("""{"daily":{"time":[],"weather_code":[],"temperature_2m_max":[],"temperature_2m_min":[]}}""", headers = json)
        })
        OpenMeteoClient(client).forecast(LatLng(41.9, 12.5), LocalDate(2026, 10, 24), LocalDate(2026, 10, 30))
        assertTrue("start_date=2026-10-24" in url && "end_date=2026-10-30" in url, url)
        assertTrue("timezone=auto" in url, url)
    }

    @Test
    fun parsesFrankfurterRate() {
        val parsed = FrankfurterClient.parseFrankfurter("""{"amount":1.0,"base":"EUR","date":"2026-10-22","rates":{"BRL":5.6574}}""")
        assertEquals(LocalDate(2026, 10, 22), parsed?.first)
        assertEquals(5.6574, parsed?.second)
        assertNull(FrankfurterClient.parseFrankfurter("""{"message":"not found"}"""))
    }

    @Test
    fun frankfurterUsesNewAddressAndLatestForToday() = runTest {
        var url = ""
        val client = HttpClient(MockEngine { request ->
            url = request.url.toString()
            respond("""{"amount":1.0,"base":"USD","date":"2026-10-22","rates":{"BRL":5.2}}""", headers = json)
        })
        val (date, rate) = FrankfurterClient(client).rateToBrl(Currency.USD, null)
        assertTrue(url.startsWith("https://api.frankfurter.dev/v1/latest"), url)
        assertTrue("base=USD" in url && "symbols=BRL" in url, url)
        assertEquals(LocalDate(2026, 10, 22), date)
        assertEquals(5.2, rate)
    }

    @Test
    fun convertsAndFormatsForeignMoney() {
        assertEquals(25_457, MoneyFormat.toBrlCents(4_500, 5.6571))
        assertEquals("€ 45,00", MoneyFormat.format(4_500, Currency.EUR))
        assertEquals(Currency.BRL, Currency.of("XYZ"))
    }

    @Test
    fun forecastPerDayUsesThePlaceOfTheDay() {
        val rome = LatLng(41.9028, 12.4964)
        val positions = mapOf(LocalDate(2026, 10, 24) to rome.forecastKey(), LocalDate(2026, 10, 25) to rome.forecastKey())
        val requests = WeatherPlanner.requests(positions)
        assertEquals(1, requests.size)
        assertEquals(LocalDate(2026, 10, 24)..LocalDate(2026, 10, 25), requests.single().second)

        val forecast = DayForecast(LatLng(41.9, 12.5), LocalDate(2026, 10, 25), 20.0, 12.0, 3)
        val matched = WeatherPlanner.match(positions, listOf(forecast))
        assertEquals(setOf(LocalDate(2026, 10, 25)), matched.keys)
        // Sem cidades nem lugares, não há previsão.
        assertTrue(WeatherPlanner.dayPositions(listOf(AgendaDay(LocalDate(2026, 10, 24), emptyList())), emptyList()).isEmpty())
    }
}
