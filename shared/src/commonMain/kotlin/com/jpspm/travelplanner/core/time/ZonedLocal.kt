package com.jpspm.travelplanner.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Data/hora local + fuso IANA (RNF-07). Nunca guardamos só o offset.
 *
 * Em transições de horário de verão, um horário que não existe (gap) é empurrado para frente
 * e um horário repetido (overlap) usa o offset anterior, seguindo a regra da base de fusos do sistema.
 */
data class ZonedLocal(
    val dateTime: LocalDateTime,
    val timeZoneId: String,
) {
    val date: LocalDate get() = dateTime.date

    val timeZone: TimeZone get() = timeZoneOrDefault(timeZoneId)

    fun toInstant(): Instant = dateTime.toInstant(timeZone)
}

fun timeZoneOrDefault(id: String): TimeZone =
    runCatching { TimeZone.of(id) }.getOrElse { TimeZone.currentSystemDefault() }

fun isValidTimeZoneId(id: String): Boolean = runCatching { TimeZone.of(id) }.isSuccess
