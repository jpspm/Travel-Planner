package com.jpspm.travelplanner.domain.export

import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.Trip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Exporta o roteiro para calendário em `.ics` (RF-46, RFC 5545). Horários em UTC (`...Z`), que todo
 * calendário entende sem precisar das definições de fuso; o calendário mostra no fuso do celular.
 * Atividades sem horário viram eventos de dia inteiro. Nada de códigos, valores ou anexos.
 */
object TripCalendar {

    /** Rótulos já em pt-BR (ex.: "Check-in", "Voo"), resolvidos pela UI. */
    class Labels(val moment: (AgendaItem.ReservationItem) -> String, val type: (AgendaItem.ReservationItem) -> String)

    fun build(trip: Trip, days: List<AgendaDay>, labels: Labels, now: Instant): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Travel Planner//PT-BR",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${text(trip.name)}",
        )
        val stamp = utc(now)
        for (day in days) for (item in day.items) {
            lines += "BEGIN:VEVENT"
            lines += "UID:${item.key.replace(':', '-')}@travelplanner"
            lines += "DTSTAMP:$stamp"
            when (item) {
                is AgendaItem.ActivityItem -> {
                    val a = item.activity
                    val start = a.start
                    if (start == null) {
                        lines += "DTSTART;VALUE=DATE:${date(a.date)}"
                        lines += "DTEND;VALUE=DATE:${date(LocalDate.fromEpochDays(a.date.toEpochDays() + 1))}"
                    } else {
                        val begin = start.toInstant()
                        val end = a.end?.toInstant()?.takeIf { it > begin } ?: (begin + 1.hours)
                        lines += "DTSTART:${utc(begin)}"
                        lines += "DTEND:${utc(end)}"
                    }
                    lines += "SUMMARY:${text(a.title)}"
                    val description = listOf(a.notes, trip.name).filter { it.isNotBlank() }.joinToString("\n")
                    if (description.isNotBlank()) lines += "DESCRIPTION:${text(description)}"
                    placeText(a.place?.name, a.place?.address)?.let { lines += "LOCATION:${text(it)}" }
                    geo(a.place?.latitude, a.place?.longitude)?.let { lines += it }
                }
                is AgendaItem.ReservationItem -> {
                    val r = item.reservation
                    val begin = item.moment.toInstant()
                    // Voo e trem ocupam até a chegada; os demais momentos (check-in, check-out...) duram 1 h.
                    val end = r.end?.toInstant()
                        ?.takeIf { item.role == AgendaItem.ReservationItem.Role.START && !r.type.endIsSeparateAgendaItem && it > begin }
                        ?: (begin + 1.hours)
                    lines += "DTSTART:${utc(begin)}"
                    lines += "DTEND:${utc(end)}"
                    lines += "SUMMARY:${text("${labels.moment(item)}: ${r.title.ifBlank { labels.type(item) }}")}"
                    lines += "DESCRIPTION:${text("${labels.type(item)} · ${trip.name}")}"
                    placeText(r.place?.name, r.place?.address)?.let { lines += "LOCATION:${text(it)}" }
                    geo(r.place?.latitude, r.place?.longitude)?.let { lines += it }
                }
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    private fun placeText(name: String?, address: String?): String? =
        listOfNotNull(name, address).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }

    private fun geo(lat: Double?, lon: Double?): String? = if (lat != null && lon != null) "GEO:$lat;$lon" else null

    private fun utc(instant: Instant): String {
        val t = instant.toLocalDateTime(TimeZone.UTC)
        return "${t.year}${pad(t.month.number)}${pad(t.day)}T${pad(t.hour)}${pad(t.minute)}${pad(t.second)}Z"
    }

    private fun date(d: LocalDate) = "${d.year}${pad(d.month.number)}${pad(d.day)}"

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    /** Escapa texto conforme o RFC 5545: barra, ponto e vírgula, vírgula e quebra de linha. */
    fun text(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    /** Quebra linhas com mais de 75 bytes, continuando com um espaço (RFC 5545, seção 3.1). */
    fun fold(line: String): String {
        val out = StringBuilder()
        var bytes = 0
        for (ch in line) {
            val size = ch.toString().encodeToByteArray().size
            if (bytes + size > 75) {
                out.append("\r\n ")
                bytes = 1
            }
            out.append(ch)
            bytes += size
        }
        return out.toString()
    }
}
