package com.jpspm.travelplanner.core.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlin.math.abs
import kotlin.time.Instant

/** Formatação pt-BR de datas, horas e fusos. Isolada aqui para facilitar i18n futuro (RNF-17). */
object TimeFormat {

    private val monthsShort = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    fun time(time: LocalTime): String = "${time.hour.pad()}:${time.minute.pad()}"

    /** `22/10/2026` */
    fun date(date: LocalDate): String = "${date.day.pad()}/${date.month.number.pad()}/${date.year}"

    /** `22 out` */
    fun dayMonth(date: LocalDate): String = "${date.day} ${monthsShort[date.month.number - 1]}"

    /** `qui, 22 out` */
    fun weekdayDayMonth(date: LocalDate): String = "${weekdayShort(date.dayOfWeek)}, ${dayMonth(date)}"

    /** `22 out – 3 nov 2026`, omitindo o que se repete. */
    fun range(start: LocalDate, end: LocalDate): String = when {
        start == end -> "${dayMonth(start)} ${start.year}"
        start.year != end.year -> "${dayMonth(start)} ${start.year} – ${dayMonth(end)} ${end.year}"
        start.month == end.month -> "${start.day} – ${dayMonth(end)} ${end.year}"
        else -> "${dayMonth(start)} – ${dayMonth(end)} ${end.year}"
    }

    fun weekdayShort(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "seg"
        DayOfWeek.TUESDAY -> "ter"
        DayOfWeek.WEDNESDAY -> "qua"
        DayOfWeek.THURSDAY -> "qui"
        DayOfWeek.FRIDAY -> "sex"
        DayOfWeek.SATURDAY -> "sáb"
        DayOfWeek.SUNDAY -> "dom"
    }

    /** `UTC+1`, `UTC-3`, `UTC+5:30`, `UTC` */
    fun utcOffset(offset: UtcOffset): String {
        val total = offset.totalSeconds
        if (total == 0) return "UTC"
        val sign = if (total > 0) "+" else "-"
        val hours = abs(total) / 3600
        val minutes = (abs(total) % 3600) / 60
        return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.pad()}"
    }

    /** Nome legível de um fuso IANA: `Europe/Rome` → `Rome`, `America/Sao_Paulo` → `Sao Paulo`. */
    fun zoneCity(timeZoneId: String): String =
        timeZoneId.substringAfterLast('/').replace('_', ' ')

    /**
     * Rótulo do fuso no instante dado (RF-08), ex.: `Roma, UTC+1`.
     * [placeName] substitui o nome da cidade do fuso quando conhecido (ex.: nome do destino).
     */
    fun zoneLabel(timeZoneId: String, at: Instant, placeName: String? = null): String {
        val offset = timeZoneOrDefault(timeZoneId).offsetAt(at)
        val name = placeName?.takeIf { it.isNotBlank() } ?: zoneCity(timeZoneId)
        return "$name, ${utcOffset(offset)}"
    }

    /** `14:00 (Roma, UTC+1)` */
    fun timeWithZone(moment: ZonedLocal, placeName: String? = null): String =
        "${time(moment.dateTime.time)} (${zoneLabel(moment.timeZoneId, moment.toInstant(), placeName)})"

    fun timeZoneOptionLabel(timeZoneId: String, at: Instant): String =
        "${timeZoneId.replace('_', ' ')} (${utcOffset(TimeZone.of(timeZoneId).offsetAt(at))})"

    private fun Int.pad(): String = toString().padStart(2, '0')
}
