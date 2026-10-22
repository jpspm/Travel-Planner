package com.jpspm.travelplanner.domain.export

import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import kotlinx.datetime.LocalDate

/** Textos da página, já em pt-BR, resolvidos pelos recursos do app (RNF-17). */
data class HtmlTexts(
    val dayCount: String,
    val dayTitle: (Int) -> String,
    val outsideTrip: String,
    val noTime: String,
    val until: (String) -> String,
    val momentLabel: (AgendaItem.ReservationItem) -> String,
    val typeLabel: (ReservationType) -> String,
    val tripMapAlt: String,
    val dayMapAlt: String,
    val creditsTitle: String,
    val photoCredit: (place: String, credit: String) -> String,
    val mapCredit: String,
    val generatedBy: String,
)

/** Imagens embutidas, já em `data:` URI (base64). `null` = sem imagem. */
data class HtmlImages(
    val cover: String?,
    val tripMap: String?,
    val dayMaps: Map<LocalDate, String>,
    /** Foto de cada lugar, pelo id do lugar. */
    val places: Map<String, String>,
)

/**
 * Gera o HTML autocontido do roteiro (RF-27): um arquivo só, CSS embutido, imagens em base64,
 * sem JavaScript (RNF-11). Nunca inclui anexos, códigos de reserva, valores nem notas das reservas
 * (RF-28, RNF-16). Inclui os créditos das fotos e do mapa (RF-29).
 */
object TripHtml {

    fun build(
        trip: Trip,
        days: List<AgendaDay>,
        cities: List<String>,
        zoneNames: Map<String, String>,
        texts: HtmlTexts,
        images: HtmlImages,
    ): String = buildString {
        append("<!doctype html>\n<html lang=\"pt-BR\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>").append(esc(trip.name)).append("</title>\n<style>").append(CSS).append("</style>\n</head>\n<body>\n")

        // Cabeçalho
        append("<header>")
        images.cover?.let { append("<img class=\"cover\" src=\"").append(it).append("\" alt=\"\">") }
        append("<div class=\"head\"><h1>").append(esc(trip.name)).append("</h1>")
        append("<p class=\"dates\">").append(esc(TimeFormat.range(trip.startDate, trip.endDate)))
            .append(" · ").append(esc(texts.dayCount)).append("</p>")
        if (cities.isNotEmpty()) append("<p class=\"cities\">").append(esc(cities.joinToString(" → "))).append("</p>")
        append("</div></header>\n<main>\n")

        images.tripMap?.let { append("<img class=\"map\" src=\"").append(it).append("\" alt=\"").append(esc(texts.tripMapAlt)).append("\">\n") }

        // Dias
        for (day in days) {
            val inTrip = day.date in trip.startDate..trip.endDate
            val number = (day.date.toEpochDays() - trip.startDate.toEpochDays()).toInt() + 1
            append("<section class=\"day\">\n<h2>")
            append(esc(if (inTrip) texts.dayTitle(number) else texts.outsideTrip))
            append(" <span>").append(esc(TimeFormat.weekdayDayMonth(day.date))).append("</span></h2>\n")
            images.dayMaps[day.date]?.let {
                append("<img class=\"map\" src=\"").append(it).append("\" alt=\"").append(esc(texts.dayMapAlt)).append("\">\n")
            }
            if (day.items.isNotEmpty()) {
                append("<ol>\n")
                day.items.forEach { appendItem(it, zoneNames, texts, images) }
                append("</ol>\n")
            }
            append("</section>\n")
        }

        // Créditos (RF-29)
        val credited = days.flatMap { d -> d.items.mapNotNull { it.place() } }
            .filter { it.photoCredit != null && images.places.containsKey(it.id) }
            .distinctBy { it.id }
        append("</main>\n<footer>\n<h3>").append(esc(texts.creditsTitle)).append("</h3>\n<ul>\n")
        append("<li>").append(esc(texts.mapCredit)).append("</li>\n")
        credited.forEach { p ->
            append("<li>").append(esc(texts.photoCredit(p.name, p.photoCredit!!)))
            p.photoSourceUrl?.takeIf { it.startsWith("https://") }?.let { append(" — <a href=\"").append(esc(it)).append("\">").append(esc(it)).append("</a>") }
            append("</li>\n")
        }
        append("</ul>\n<p class=\"by\">").append(esc(texts.generatedBy)).append("</p>\n</footer>\n</body>\n</html>\n")
    }

    private fun StringBuilder.appendItem(item: AgendaItem, zoneNames: Map<String, String>, texts: HtmlTexts, images: HtmlImages) {
        val moment = item.moment
        append("<li><div class=\"time\">")
        if (moment != null) {
            append(esc(TimeFormat.time(moment.dateTime.time)))
            val zone = TimeFormat.zoneLabel(moment.timeZoneId, moment.toInstant(), zoneNames[moment.timeZoneId])
            append("<small>").append(esc(zone)).append("</small>")
        } else {
            append("<small>").append(esc(texts.noTime)).append("</small>")
        }
        append("</div><div class=\"body\">")
        when (item) {
            is AgendaItem.ActivityItem -> {
                append("<strong>").append(esc(item.activity.title)).append("</strong>")
                item.activity.endTime?.let { end ->
                    append("<span class=\"meta\">").append(esc(texts.until(TimeFormat.time(end)))).append("</span>")
                }
                appendPlace(item.activity.place, item.activity.title)
                item.activity.notes.takeIf { it.isNotBlank() }?.let { append("<p class=\"notes\">").append(esc(it)).append("</p>") }
            }
            is AgendaItem.ReservationItem -> {
                // Só título, tipo, momento e local: nada de código, valor, notas ou anexos (RF-28).
                val r = item.reservation
                append("<strong>").append(esc(r.title.ifBlank { texts.typeLabel(r.type) })).append("</strong>")
                append("<span class=\"meta\">").append(esc("${texts.typeLabel(r.type)} · ${texts.momentLabel(item)}")).append("</span>")
                val end = r.end
                if (item.role == AgendaItem.ReservationItem.Role.START && end != null && !r.type.endIsSeparateAgendaItem) {
                    val zone = TimeFormat.zoneLabel(end.timeZoneId, end.toInstant(), zoneNames[end.timeZoneId])
                    val shift = (end.date.toEpochDays() - item.moment.date.toEpochDays()).toInt()
                    val shiftText = if (shift != 0) " (${if (shift > 0) "+" else ""}$shift)" else ""
                    append("<span class=\"meta\">").append(esc(texts.until("${TimeFormat.time(end.dateTime.time)} ($zone)$shiftText"))).append("</span>")
                }
                appendPlace(r.place, r.title)
            }
        }
        append("</div>")
        item.place()?.let { images.places[it.id] }?.let { append("<img class=\"thumb\" src=\"").append(it).append("\" alt=\"\">") }
        append("</li>\n")
    }

    private fun StringBuilder.appendPlace(place: Place?, title: String) {
        place ?: return
        val text = listOf(place.name.takeIf { it != title }.orEmpty(), place.address).filter { it.isNotBlank() }.joinToString(" · ")
        if (text.isNotBlank()) append("<span class=\"place\">").append(esc(text)).append("</span>")
    }

    /** Escapa texto digitado pelo usuário para não quebrar nem injetar HTML. */
    fun esc(text: String): String = buildString(text.length) {
        text.forEach {
            when (it) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(it)
            }
        }
    }

    private const val CSS = """
:root{--bg:#f8f9fc;--card:#fff;--text:#191c1e;--muted:#5b6770;--primary:#00658e;--line:#dde3ea}
@media (prefers-color-scheme:dark){:root{--bg:#111416;--card:#1d2023;--text:#e1e2e5;--muted:#9aa4ab;--primary:#85cfff;--line:#2c3237}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
header{position:relative;background:var(--primary);color:#fff}
header .cover{display:block;width:100%;max-height:320px;object-fit:cover;opacity:.55}
header .head{padding:24px 16px}
header .cover+.head{position:absolute;bottom:0;left:0;right:0;background:linear-gradient(transparent,rgba(0,0,0,.6))}
h1{margin:0;font-size:28px}
.dates,.cities{margin:4px 0 0;opacity:.9}
main{max-width:760px;margin:0 auto;padding:16px}
img.map{display:block;width:100%;border-radius:12px;margin:8px 0 16px}
.day{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;margin:16px 0}
.day h2{margin:0 0 8px;font-size:20px;color:var(--primary)}
.day h2 span{font-weight:400;color:var(--muted);font-size:15px;margin-left:6px}
ol{list-style:none;margin:0;padding:0}
li{display:flex;gap:12px;align-items:flex-start;padding:10px 0;border-top:1px solid var(--line)}
li:first-child{border-top:0}
.time{min-width:84px;font-weight:600}
.time small{display:block;font-weight:400;color:var(--muted);font-size:12px}
.body{flex:1;min-width:0}
.body strong{display:block}
.meta,.place{display:block;color:var(--muted);font-size:14px}
.notes{margin:4px 0 0;font-size:14px;white-space:pre-line}
.thumb{width:64px;height:64px;object-fit:cover;border-radius:10px;flex:none}
footer{max-width:760px;margin:0 auto;padding:16px 16px 32px;color:var(--muted);font-size:13px}
footer h3{font-size:14px;margin:0 0 6px}
footer ul{padding-left:18px;margin:0}
footer a{color:var(--muted);word-break:break-all}
.by{margin-top:12px}
@media (max-width:420px){.time{min-width:68px}.thumb{width:52px;height:52px}}
"""
}
