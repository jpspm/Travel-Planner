package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.domain.importing.BookingParser
import com.jpspm.travelplanner.domain.importing.DateScanner
import com.jpspm.travelplanner.domain.importing.MoneyScanner
import com.jpspm.travelplanner.domain.model.ReservationField
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingParserTest {

    private val today = LocalDate(2026, 10, 23)

    @Test
    fun latamETicketInPortuguese() {
        val text = """
            LATAM Airlines
            Seu cartão de embarque
            Código de reserva: XKJQ7B
            Data de emissão: 01/09/2026
            Passageiro: JOAO SILVA
            Voo LA 8084
            Sáb, 14 de novembro de 2026
            Partida 18:35 São Paulo (GRU) Terminal 3
            Chegada 10:05 Roma (FCO)
            Assento 32C   Portão 15
            Valor total pago: R$ 4.873,62
        """.trimIndent()
        val draft = BookingParser.parse(text, today)
        assertEquals(ReservationType.FLIGHT, draft.type)
        assertEquals("XKJQ7B", draft.confirmationCode)
        assertEquals("LA8084", draft.details[ReservationField.FLIGHT_NUMBER])
        assertEquals("LATAM", draft.details[ReservationField.AIRLINE])
        assertEquals("GRU", draft.details[ReservationField.ORIGIN])
        assertEquals("FCO", draft.details[ReservationField.DESTINATION])
        assertEquals("32C", draft.details[ReservationField.SEAT])
        assertEquals("15", draft.details[ReservationField.GATE])
        assertEquals("3", draft.details[ReservationField.TERMINAL])
        // A data de emissão é ignorada; partida e chegada no horário local de cada aeroporto.
        assertEquals(LocalDateTime(2026, 11, 14, 18, 35), draft.start)
        assertEquals("America/Sao_Paulo", draft.startZone)
        assertEquals("Europe/Rome", draft.endZone)
        assertEquals(487_362L, draft.priceCents)
        assertEquals(Currency.BRL, draft.currency)
    }

    @Test
    fun flightArrivalNextDayWithItsOwnDate() {
        val text = """
            Flight confirmation
            Booking reference: ABC123
            TP 102  LIS → GRU
            Departure: 12 Dec 2026 21:40
            Arrival: 13 Dec 2026 05:10
        """.trimIndent()
        val draft = BookingParser.parse(text, today)
        assertEquals(ReservationType.FLIGHT, draft.type)
        assertEquals("TAP", draft.details[ReservationField.AIRLINE])
        assertEquals("TP102", draft.details[ReservationField.FLIGHT_NUMBER])
        assertEquals(LocalDateTime(2026, 12, 12, 21, 40), draft.start)
        assertEquals(LocalDateTime(2026, 12, 13, 5, 10), draft.end)
        assertEquals("Europe/Lisbon", draft.startZone)
    }

    @Test
    fun bookingComHotelConfirmation() {
        val text = """
            Booking.com
            Sua reserva está confirmada
            Hotel Artemide
            Endereço: Via Nazionale, 22, 00184 Roma, Itália
            Número da reserva: 4821.553.901
            Check-in
            sexta-feira, 15 de novembro de 2026
            a partir das 14:00
            Check-out
            domingo, 17 de novembro de 2026
            até 11:00
            2 noites, 1 quarto
            Tipo de quarto: Quarto Duplo Superior
            Preço total € 389,40
        """.trimIndent()
        val draft = BookingParser.parse(text, today, "Booking.com_confirmacao.pdf")
        assertEquals(ReservationType.LODGING, draft.type)
        assertEquals("Hotel Artemide", draft.title)
        assertEquals("4821.553.901", draft.confirmationCode)
        assertEquals(LocalDateTime(2026, 11, 15, 14, 0), draft.start)
        assertEquals(LocalDateTime(2026, 11, 17, 11, 0), draft.end)
        assertEquals("Via Nazionale, 22, 00184 Roma, Itália", draft.address)
        assertEquals("Quarto Duplo Superior", draft.details[ReservationField.ROOM_TYPE])
        assertEquals(38_940L, draft.priceCents)
        assertEquals(Currency.EUR, draft.currency)
    }

    @Test
    fun trenitaliaTicket() {
        val text = """
            Trenitalia
            Biglietto / Ticket
            PNR: X7K2MP
            Frecciarossa 9512
            From: Roma Termini
            To: Firenze S. M. Novella
            18/11/2026 09:15 - 10:47
            Carrozza 6 Posto 12A
            Totale 29,90 €
        """.trimIndent()
        val draft = BookingParser.parse(text, today)
        assertEquals(ReservationType.TRAIN_BUS, draft.type)
        assertEquals("Trenitalia", draft.details[ReservationField.COMPANY])
        assertEquals("9512", draft.details[ReservationField.SERVICE_NUMBER])
        assertEquals("Roma Termini", draft.details[ReservationField.ORIGIN])
        assertEquals("Firenze S. M. Novella", draft.details[ReservationField.DESTINATION])
        assertEquals("Carro 6, assento 12A", draft.details[ReservationField.SEAT_COACH])
        assertEquals(LocalDateTime(2026, 11, 18, 9, 15), draft.start)
        assertEquals(LocalDateTime(2026, 11, 18, 10, 47), draft.end)
        assertEquals("X7K2MP", draft.confirmationCode)
        assertEquals(2_990L, draft.priceCents)
    }

    @Test
    fun carRentalPickupAndReturn() {
        val text = """
            Localiza - Aluguel de carro
            Reserva nº 98765432
            Grupo C - Econômico com ar
            Retirada: Aeroporto de Florianópolis 20/12/2026 10:00
            Devolução: Aeroporto de Florianópolis 27/12/2026 10:00
            Valor total R$ 1.250,00
        """.trimIndent()
        val draft = BookingParser.parse(text, today)
        assertEquals(ReservationType.CAR_RENTAL, draft.type)
        assertEquals("Localiza", draft.details[ReservationField.COMPANY])
        assertEquals("Aeroporto de Florianópolis", draft.details[ReservationField.PICKUP_LOCATION])
        assertEquals(LocalDateTime(2026, 12, 20, 10, 0), draft.start)
        assertEquals(LocalDateTime(2026, 12, 27, 10, 0), draft.end)
        assertEquals("98765432", draft.confirmationCode)
        assertEquals(125_000L, draft.priceCents)
    }

    @Test
    fun unknownTextKeepsTypeOpenAndUsesFileName() {
        val draft = BookingParser.parse("Obrigado pela preferência.", today, "comprovante_museu.pdf")
        assertNull(draft.type)
        assertEquals("comprovante museu", draft.title)
        assertNull(draft.start)
    }

    @Test
    fun scansDatesInSeveralFormats() {
        fun first(line: String) = DateScanner.dates(line, today).firstOrNull()?.second
        assertEquals(LocalDate(2026, 11, 14), first("14/11/2026"))
        assertEquals(LocalDate(2026, 11, 14), first("2026-11-14"))
        assertEquals(LocalDate(2026, 11, 14), first("14 de novembro de 2026"))
        assertEquals(LocalDate(2026, 11, 14), first("November 14, 2026"))
        assertEquals(LocalDate(2026, 11, 14), first("14NOV26"))
        assertEquals(LocalDate(2026, 11, 14), first("sáb., 14 nov."))
        // Sem ano e já passou há mais de 2 meses: vai para o ano seguinte.
        assertEquals(LocalDate(2027, 3, 2), first("2 de março"))
        // "out" sozinho é palavra comum, não outubro.
        assertNull(first("2 out of 3"))
    }

    @Test
    fun parsesMoneyInBothNotations() {
        assertEquals(123_456L, MoneyScanner.cents("1.234,56"))
        assertEquals(123_456L, MoneyScanner.cents("1,234.56"))
        assertEquals(4_500L, MoneyScanner.cents("45"))
        assertEquals(listOf(19_900L to Currency.USD), MoneyScanner.amounts("Total: US$ 199.00"))
    }
}
