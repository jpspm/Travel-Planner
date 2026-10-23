package com.jpspm.travelplanner.data.remote

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.weather.DayForecast
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

private val json = Json { ignoreUnknownKeys = true }

/** Previsão diária do Open-Meteo (RF-47): gratuito, sem chave, até 16 dias à frente. */
class OpenMeteoClient(private val client: HttpClient) {

    suspend fun forecast(position: LatLng, from: LocalDate, to: LocalDate): List<DayForecast> {
        val response = client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", position.latitude)
            parameter("longitude", position.longitude)
            parameter("daily", "weather_code,temperature_2m_max,temperature_2m_min")
            parameter("timezone", "auto")
            parameter("start_date", from.toString())
            parameter("end_date", to.toString())
        }
        check(response.status.isSuccess()) { "Open-Meteo respondeu ${response.status}" }
        return parseOpenMeteo(response.bodyAsText(), position)
    }

    companion object {
        internal fun parseOpenMeteo(body: String, position: LatLng): List<DayForecast> {
            val daily = json.parseToJsonElement(body).jsonObject["daily"] as? JsonObject ?: return emptyList()
            val dates = (daily["time"] as? JsonArray).orEmpty()
            val codes = (daily["weather_code"] as? JsonArray).orEmpty()
            val max = (daily["temperature_2m_max"] as? JsonArray).orEmpty()
            val min = (daily["temperature_2m_min"] as? JsonArray).orEmpty()
            return dates.indices.mapNotNull { i ->
                val date = (dates[i] as? JsonPrimitive)?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@mapNotNull null
                DayForecast(
                    position = position,
                    date = date,
                    maxC = (max.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null,
                    minC = (min.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null,
                    code = (codes.getOrNull(i) as? JsonPrimitive)?.intOrNull ?: -1,
                )
            }
        }
    }
}

/** Cotações de referência do Banco Central Europeu pelo Frankfurter (RF-48): gratuito, sem chave. */
class FrankfurterClient(private val client: HttpClient) {

    /** Quantos BRL vale 1 unidade de [currency] em [date] (`null` = a mais recente), com a data da cotação. */
    suspend fun rateToBrl(currency: Currency, date: LocalDate?): Pair<LocalDate, Double> {
        val response = client.get("https://api.frankfurter.dev/v1/${date?.toString() ?: "latest"}") {
            parameter("base", currency.name)
            parameter("symbols", "BRL")
        }
        check(response.status.isSuccess()) { "Frankfurter respondeu ${response.status}" }
        return parseFrankfurter(response.bodyAsText()) ?: error("Cotação de ${currency.name} indisponível")
    }

    companion object {
        /** `{"amount":1.0,"base":"EUR","date":"2026-10-22","rates":{"BRL":5.6574}}` → data e taxa. */
        internal fun parseFrankfurter(body: String): Pair<LocalDate, Double>? {
            val root = json.parseToJsonElement(body).jsonObject
            val date = (root["date"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return null
            val rate = ((root["rates"] as? JsonObject)?.get("BRL") as? JsonPrimitive)?.doubleOrNull ?: return null
            val amount = (root["amount"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 } ?: 1.0
            return date to rate / amount
        }
    }
}
