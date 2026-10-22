package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.export.HtmlImages
import com.jpspm.travelplanner.domain.export.HtmlTexts
import com.jpspm.travelplanner.domain.export.TripHtml
import com.jpspm.travelplanner.domain.model.Activity
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.model.Trip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TripHtmlTest {

    private val d1 = LocalDate(2027, 5, 1)
    private val trip = Trip("t", "Itália <2027>", d1, LocalDate(2027, 5, 2), null, "", null, Instant.DISTANT_PAST, Instant.DISTANT_PAST)

    private val colosseo = Place(
        "p1", "t", "Coliseu", "Piazza del Colosseo, Roma", 41.89, 12.49, "IT", "Roma", "Europe/Rome", null,
        "/photos/c.jpg", "Fulano", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Colosseo.jpg",
    )

    private val flight = Reservation(
        id = "r1", tripId = "t", type = ReservationType.FLIGHT, title = "LATAM LA8084 · GRU → FCO", place = null,
        start = ZonedLocal(LocalDateTime(2027, 5, 1, 22, 0), "America/Sao_Paulo"),
        end = ZonedLocal(LocalDateTime(2027, 5, 2, 14, 0), "Europe/Rome"),
        confirmationCode = "XYZ987", priceCents = 345_678, notes = "assento perto da janela, cartão final 4321",
        details = mapOf("seat" to "23A"),
        attachments = listOf(Attachment("a1", "r1", AttachmentKind.QR, "boarding.png", "/attachments/qr.png", Instant.DISTANT_PAST)),
    )

    private val activity = Activity(
        "act", "t", d1, LocalTime(9, 0), LocalTime(11, 0), "Europe/Rome",
        "Visita <b>guiada</b> & \"tour\"", "Levar água", colosseo, 0,
    )

    private val texts = HtmlTexts(
        dayCount = "2 dias",
        dayTitle = { "Dia $it" },
        outsideTrip = "Fora do período",
        noTime = "Sem horário",
        until = { "até $it" },
        momentLabel = { "Partida" },
        typeLabel = { "Voo" },
        tripMapAlt = "Mapa da viagem",
        dayMapAlt = "Mapa do dia",
        creditsTitle = "Créditos",
        photoCredit = { place, credit -> "Foto de $place: $credit" },
        mapCredit = "Mapas: © OpenStreetMap contributors",
        generatedBy = "Gerado pelo Travel Planner",
    )

    private fun html(): String {
        val days = AgendaBuilder.build(trip.days, listOf(activity), listOf(flight))
        return TripHtml.build(
            trip, days, listOf("Roma"), mapOf("Europe/Rome" to "Roma"), texts,
            HtmlImages(cover = null, tripMap = "data:image/jpeg;base64,MAPA", dayMaps = emptyMap(), places = mapOf("p1" to "data:image/jpeg;base64,FOTO")),
        )
    }

    @Test
    fun neverLeaksCodesPricesNotesOrAttachmentsOfReservations() {
        val page = html()
        assertFalse("XYZ987" in page, "código da reserva")
        assertFalse("3.456" in page || "345678" in page || "R$" in page, "valor pago")
        assertFalse("cartão final" in page, "notas da reserva")
        assertFalse("boarding.png" in page || "qr.png" in page, "anexos")
        assertFalse("23A" in page, "detalhes da reserva")
        assertTrue("LATAM LA8084" in page)
    }

    @Test
    fun escapesUserText() {
        val page = html()
        assertTrue("Itália &lt;2027&gt;" in page)
        assertTrue("Visita &lt;b&gt;guiada&lt;/b&gt; &amp; &quot;tour&quot;" in page)
        assertFalse("<b>guiada</b>" in page)
    }

    @Test
    fun selfContainedWithTimesZonesImagesAndCredits() {
        val page = html()
        assertTrue(page.startsWith("<!doctype html>"))
        assertFalse("<script" in page, "sem JavaScript")
        assertFalse("http://" in page, "nada carregado da rede")
        assertTrue("09:00" in page && "Roma, UTC+2" in page)
        assertTrue("22:00" in page && "Sao Paulo, UTC-3" in page)
        assertTrue("(+1)" in page, "chegada no dia seguinte")
        assertTrue("data:image/jpeg;base64,MAPA" in page && "data:image/jpeg;base64,FOTO" in page)
        assertTrue("Foto de Coliseu: Fulano · CC BY-SA 4.0 · Wikimedia Commons" in page)
        assertTrue("OpenStreetMap" in page)
        assertTrue("Levar água" in page, "notas da atividade entram")
    }

    @Test
    fun escapeHelper() {
        assertEquals("&lt;a href=&quot;x&quot;&gt;&amp;&#39;", TripHtml.esc("<a href=\"x\">&'"))
    }
}
