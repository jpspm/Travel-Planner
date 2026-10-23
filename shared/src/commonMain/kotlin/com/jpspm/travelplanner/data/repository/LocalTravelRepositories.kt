package com.jpspm.travelplanner.data.repository

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.local.ExchangeRateEntity
import com.jpspm.travelplanner.data.local.TravelerEntity
import com.jpspm.travelplanner.data.local.WeatherEntity
import com.jpspm.travelplanner.data.remote.FrankfurterClient
import com.jpspm.travelplanner.data.remote.OpenMeteoClient
import com.jpspm.travelplanner.domain.budget.Traveler
import com.jpspm.travelplanner.domain.repository.CurrencyRepository
import com.jpspm.travelplanner.domain.repository.ExchangeRate
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.TravelerRepository
import com.jpspm.travelplanner.domain.weather.DayForecast
import com.jpspm.travelplanner.domain.weather.WeatherRepository
import com.jpspm.travelplanner.domain.weather.forecastKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class LocalTravelerRepository(
    private val db: AppDatabase,
    private val clock: Clock,
) : TravelerRepository {

    override fun observe(tripId: String): Flow<List<Traveler>> =
        db.travelerDao().observeByTrip(tripId).map { list -> list.map { Traveler(it.id, it.tripId, it.name) } }

    override suspend fun add(tripId: String, name: String) {
        if (name.isBlank()) return
        val now = clock.now().toEpochMilliseconds()
        val order = db.travelerDao().maxSortOrder(tripId) + 1
        db.travelerDao().upsert(TravelerEntity(newId(), tripId, name.trim(), order, now, now))
    }

    override suspend fun rename(id: String, name: String) {
        val traveler = db.travelerDao().get(id) ?: return
        if (name.isBlank()) return
        db.travelerDao().upsert(traveler.copy(name = name.trim(), updatedAt = clock.now().toEpochMilliseconds()))
    }

    /** Gastos que ele pagou passam a contar como do primeiro viajante (regra de [SplitCalculator]). */
    override suspend fun delete(id: String) = db.travelerDao().delete(id)
}

class LocalWeatherRepository(
    private val db: AppDatabase,
    private val client: OpenMeteoClient,
    private val clock: Clock,
) : WeatherRepository {

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<DayForecast>> =
        db.weatherDao().observeBetween(from.toString(), to.toString()).map { list ->
            list.map { DayForecast(LatLng(it.latitude, it.longitude), LocalDate.parse(it.date), it.maxC, it.minC, it.code) }
        }

    override suspend fun refresh(requests: List<Pair<LatLng, ClosedRange<LocalDate>>>, today: LocalDate) {
        val now = clock.now()
        val lastDay = today.plus(DatePeriod(days = FORECAST_DAYS - 1))
        db.weatherDao().deleteBefore(today.minus(DatePeriod(months = 1)).toString())
        requests.forEach { (position, range) ->
            val key = position.forecastKey()
            val from = maxOf(range.start, today)
            val to = minOf(range.endInclusive, lastDay)
            if (from > to) return@forEach
            val oldest = db.weatherDao().oldestUpdate(key.latitude, key.longitude, from.toString(), to.toString())
            if (oldest != null && now.toEpochMilliseconds() - oldest < STALE_AFTER.inWholeMilliseconds) return@forEach
            try {
                val days = client.forecast(key, from, to)
                db.weatherDao().upsertAll(
                    days.map { WeatherEntity(key.latitude, key.longitude, it.date.toString(), it.maxC, it.minC, it.code, now.toEpochMilliseconds()) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sem internet ou serviço fora do ar: fica a previsão salva, se houver.
            }
        }
    }

    private companion object {
        const val FORECAST_DAYS = 16
        val STALE_AFTER = 3.hours
    }
}

class LocalCurrencyRepository(
    private val db: AppDatabase,
    private val client: FrankfurterClient,
    private val clock: Clock,
) : CurrencyRepository {

    override suspend fun rate(currency: Currency, date: LocalDate): ExchangeRate? {
        if (currency == Currency.BRL) return ExchangeRate(currency, 1.0, date, offline = false)
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dao = db.exchangeRateDao()
        // Dias passados têm cotação fixa: se já está salva, não precisa da internet.
        if (date < today) dao.get(currency.name, date.toString())?.let { return it.toDomain(offline = false) }
        return try {
            val (rateDate, rate) = client.rateToBrl(currency, date.takeIf { it < today })
            dao.upsert(ExchangeRateEntity(currency.name, (if (date < today) date else rateDate).toString(), rate, clock.now().toEpochMilliseconds()))
            ExchangeRate(currency, rate, rateDate, offline = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            (dao.latestUntil(currency.name, date.toString()) ?: dao.latest(currency.name))?.toDomain(offline = true)
        }
    }

    private fun ExchangeRateEntity.toDomain(offline: Boolean) =
        ExchangeRate(Currency.of(currency), rateToBrl, LocalDate.parse(date), offline)
}
