package com.jpspm.travelplanner.domain.weather

import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.city.CityGroup
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.repository.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** Previsão de um dia num lugar (RF-47). */
data class DayForecast(val position: LatLng, val date: LocalDate, val maxC: Double, val minC: Double, val code: Int) {
    val emoji: String get() = WeatherCodes.emoji(code)

    /** `☀️ 24° / 15°` */
    val short: String get() = "$emoji ${maxC.roundToInt()}° / ${minC.roundToInt()}°"
}

/** Códigos de tempo da OMM (WMO) usados pelo Open-Meteo. */
object WeatherCodes {
    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /** Descrição curta em pt-BR. */
    fun description(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Poucas nuvens"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55 -> "Garoa"
        56, 57 -> "Garoa congelante"
        61, 63, 65 -> "Chuva"
        66, 67 -> "Chuva congelante"
        71, 73, 75, 77 -> "Neve"
        80, 81, 82 -> "Pancadas de chuva"
        85, 86 -> "Pancadas de neve"
        95 -> "Trovoadas"
        96, 99 -> "Trovoadas com granizo"
        else -> "Tempo"
    }
}

/** Previsão do tempo salva no aparelho e atualizada quando há internet (RF-47, RNF-23). */
interface WeatherRepository {
    fun observe(from: LocalDate, to: LocalDate): Flow<List<DayForecast>>

    /**
     * Atualiza a previsão de cada lugar para o período pedido, só para os dias dentro da janela de
     * previsão (~16 dias) e se a última atualização tiver mais de 3 horas.
     */
    suspend fun refresh(requests: List<Pair<LatLng, ClosedRange<LocalDate>>>, today: LocalDate)
}

/** Arredonda para 2 casas (~1 km), a mesma chave usada para salvar a previsão. */
fun LatLng.forecastKey(): LatLng = LatLng((latitude * 100).roundToInt() / 100.0, (longitude * 100).roundToInt() / 100.0)

/** Onde buscar a previsão de cada dia do roteiro. */
object WeatherPlanner {

    /**
     * Posição de cada dia: a cidade cujo período cobre o dia (a que começa por último, no dia de
     * mudança de cidade); sem cidade, o primeiro lugar com coordenadas do dia.
     */
    fun dayPositions(days: List<AgendaDay>, cities: List<CityGroup>): Map<LocalDate, LatLng> =
        days.mapNotNull { day ->
            val city = cities.filter { c ->
                c.center != null && c.startDate != null && c.endDate != null && day.date in c.startDate..c.endDate
            }.maxByOrNull { it.startDate!! }
            val position = city?.center
                ?: day.items.firstNotNullOfOrNull { item -> item.place()?.takeIf { it.hasPosition }?.let { LatLng(it.latitude!!, it.longitude!!) } }
            position?.let { day.date to it.forecastKey() }
        }.toMap()

    /** Um pedido por lugar, cobrindo do primeiro ao último dia em que ele aparece. */
    fun requests(positions: Map<LocalDate, LatLng>): List<Pair<LatLng, ClosedRange<LocalDate>>> =
        positions.entries.groupBy({ it.value }, { it.key }).map { (position, dates) -> position to dates.min()..dates.max() }

    /** Previsão de cada dia, quando há uma salva para o lugar do dia. */
    fun match(positions: Map<LocalDate, LatLng>, forecasts: List<DayForecast>): Map<LocalDate, DayForecast> {
        val byKey = forecasts.associateBy { it.position.forecastKey() to it.date }
        return positions.mapNotNull { (date, position) -> byKey[position to date]?.let { date to it } }.toMap()
    }
}
