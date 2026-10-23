package com.jpspm.travelplanner.domain.importing

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.domain.model.ReservationField
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime

/** Reserva lida de um PDF (RF-50): tudo opcional, para o usuário conferir antes de salvar. */
data class BookingDraft(
    /** `null` quando o texto não deixa claro o tipo. */
    val type: ReservationType?,
    val title: String = "",
    val details: Map<ReservationField, String> = emptyMap(),
    val confirmationCode: String? = null,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    /** Só a data, quando o horário não foi encontrado. */
    val startDate: LocalDate? = start?.date,
    val endDate: LocalDate? = end?.date,
    val startZone: String? = null,
    val endZone: String? = null,
    val priceCents: Long? = null,
    val currency: Currency = Currency.BRL,
    val address: String? = null,
) {
    /** Quantos campos foram reconhecidos, para dizer ao usuário se a leitura foi boa. */
    val recognized: Int
        get() = listOfNotNull(type, title.ifBlank { null }, confirmationCode, startDate, endDate, priceCents, address).size + details.size
}

/**
 * Lê o texto de confirmações de reserva (e-mail salvo em PDF, cartão de embarque, voucher) em
 * português, inglês, espanhol, italiano e francês. São regras simples, sem rede: o resultado é um
 * rascunho que o usuário confere no formulário.
 */
object BookingParser {

    fun parse(text: String, today: LocalDate, fileName: String = ""): BookingDraft {
        val lines = text.lines().map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val flat = lines.joinToString("\n")
        val type = detectType(flat)
        val events = DateScanner.events(lines, today)
        val (start, end) = pickMoments(events, lines, type)
        val details = when (type) {
            ReservationType.FLIGHT -> flightDetails(flat)
            ReservationType.TRAIN_BUS -> trainDetails(flat)
            ReservationType.CAR_RENTAL -> carDetails(lines, flat)
            ReservationType.EVENT -> labeled(lines, SECTOR_LABELS)?.let { mapOf(ReservationField.SECTOR_SEAT to it) }.orEmpty()
            ReservationType.RESTAURANT -> PEOPLE.find(flat)?.let { mapOf(ReservationField.PEOPLE to it.groupValues[1]) }.orEmpty()
            ReservationType.LODGING -> labeled(lines, ROOM_LABELS)?.let { mapOf(ReservationField.ROOM_TYPE to it) }.orEmpty()
            else -> emptyMap()
        }
        val (price, currency) = MoneyScanner.total(lines) ?: (null to Currency.BRL)
        val origin = details[ReservationField.ORIGIN]?.let(Airports::zoneOf)
        val destination = details[ReservationField.DESTINATION]?.let(Airports::zoneOf)
        return BookingDraft(
            type = type,
            title = if (type?.hasTitleField == false) "" else title(lines, type, fileName),
            details = details,
            confirmationCode = code(flat),
            start = start?.dateTime,
            end = end?.dateTime,
            startDate = start?.date,
            endDate = end?.date,
            startZone = if (type == ReservationType.FLIGHT) origin else null,
            endZone = if (type == ReservationType.FLIGHT) destination else null,
            priceCents = price,
            currency = currency,
            address = if (type?.hasPlace == true) labeled(lines, ADDRESS_LABELS) else null,
        )
    }

    // ---------- Tipo ----------

    private val TYPE_KEYWORDS: Map<ReservationType, List<String>> = mapOf(
        ReservationType.FLIGHT to listOf(
            "cartão de embarque", "boarding pass", "tarjeta de embarque", "carta d'imbarco", "carte d'embarquement",
            "e-ticket", "bilhete eletrônico", "itinerário do voo", "flight", "voo", "vuelo", "volo", "companhia aérea",
            "airline", "aeroporto", "airport", "bagagem despachada", "checked baggage", "localizador",
        ),
        ReservationType.LODGING to listOf(
            "check-in", "check-out", "hotel", "hostel", "pousada", "airbnb", "booking.com", "hospedagem", "alojamiento",
            "noites", "nights", "noches", "notti", "nuits", "quarto", "room", "habitación", "camera", "chambre", "acomodação",
        ),
        ReservationType.CAR_RENTAL to listOf(
            "aluguel de carro", "locação", "locadora", "car rental", "rental car", "alquiler de coche", "noleggio auto",
            "location de voiture", "retirada do veículo", "pick-up", "pickup", "drop-off", "devolução do veículo",
            "localiza", "movida", "unidas", "hertz", "avis", "europcar", "sixt", "rentcars",
        ),
        ReservationType.TRAIN_BUS to listOf(
            "trem", "train", "tren", "treno", "ônibus", "onibus", "rodoviária", "coach", "autobús", "autobus", "pullman",
            "trenitalia", "italo", "sncf", "ouigo", "renfe", "eurostar", "deutsche bahn", "flixbus", "alsa", "comboios",
            "carriage", "vagão", "carrozza", "coche", "poltrona",
        ),
        ReservationType.EVENT to listOf(
            "ingresso", "ticket", "entrada", "biglietto", "billet", "admission", "show", "concert", "museu", "museum",
            "museo", "musée", "setor", "sector", "fila", "row", "tour", "passeio", "excursão", "visita guiada",
        ),
        ReservationType.RESTAURANT to listOf(
            "restaurante", "restaurant", "ristorante", "mesa para", "table for", "tavolo", "thefork", "opentable",
            "reserva de mesa", "pessoas", "guests", "comensales", "coperti",
        ),
    )

    /** Pesos: palavras mais específicas (cartão de embarque, check-in) valem mais que as genéricas. */
    private val STRONG = setOf(
        "cartão de embarque", "boarding pass", "tarjeta de embarque", "carta d'imbarco", "carte d'embarquement",
        "check-in", "check-out", "car rental", "aluguel de carro", "rental car", "mesa para", "table for",
        "trenitalia", "italo", "sncf", "renfe", "flixbus", "eurostar", "booking.com", "airbnb", "localiza", "hertz",
    )

    internal fun detectType(text: String): ReservationType? {
        val lower = text.lowercase()
        val scores = TYPE_KEYWORDS.mapValues { (_, words) ->
            words.sumOf { w -> occurrences(lower, w) * if (w in STRONG) 3 else 1 }
        }.toMutableMap()
        // Número de voo com sigla de companhia conhecida é sinal forte.
        if (Airlines.findFlightNumber(text) != null) scores[ReservationType.FLIGHT] = scores.getValue(ReservationType.FLIGHT) + 4
        // "check-in" também aparece em voos ("faça o check-in"): só conta como hotel com noites/quarto.
        val best = scores.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value >= 2 }
    }

    private fun occurrences(text: String, word: String): Int {
        var count = 0
        var index = text.indexOf(word)
        while (index >= 0) {
            val before = text.getOrNull(index - 1)
            val after = text.getOrNull(index + word.length)
            if ((before == null || !before.isLetter()) && (after == null || !after.isLetter())) count++
            index = text.indexOf(word, index + word.length)
        }
        return count
    }

    // ---------- Código ----------

    private val CODE = Regex(
        "(?i)(?:localizador|c[óo]digo (?:da |de )?(?:reserva|confirma[çc][ãa]o)|n[úu]mero (?:da |de )?(?:reserva|confirma[çc][ãa]o)|" +
            "booking (?:reference|ref\\.?|number|code|id)|confirmation (?:number|code|no\\.?|#)|reservation (?:number|code)|" +
            "record locator|pnr|c[óo]digo de reserva|n[úu]mero de reserva|codice (?:di )?prenotazione|r[ée]f[ée]rence de (?:r[ée]servation|dossier)|" +
            "reserva n[º°o.]*|pedido n[º°o.]*|order (?:number|#))\\s*[:#nº°.]*\\s*\\n?\\s*([A-Za-z0-9][A-Za-z0-9.-]{3,17})",
    )

    internal fun code(text: String): String? = CODE.findAll(text)
        .map { it.groupValues[1].trimEnd('-', '.') }
        .firstOrNull { c -> c.any(Char::isDigit) || (c.length in 5..8 && c.all { it.isUpperCase() }) }
        ?.uppercase()

    // ---------- Datas ----------

    private val START_WORDS = listOf(
        "check-in", "entrada", "chegada", "arrival date", "retirada", "pick-up", "pickup", "partida", "departure", "saída",
        "embarque", "ida", "outbound", "data", "date", "fecha", "inizio", "début",
    )
    private val END_WORDS = listOf(
        "check-out", "checkout", "saída do hotel", "devolução", "drop-off", "dropoff", "return", "chegada", "arrival", "llegada", "arrivo", "arrivée",
    )
    private val IGNORE_WORDS = listOf(
        "emissão", "emitido", "emitida", "issued", "issue date", "booking date", "data da reserva", "reservado em", "booked on",
        "data da compra", "purchase date", "nascimento", "birth", "validade", "valid until", "expira", "expiry", "cancelamento grátis",
        "free cancellation", "cancel", "pagamento em", "payment date", "fatura", "invoice date", "impresso", "printed",
    )

    private fun pickMoments(events: List<DateEvent>, lines: List<String>, type: ReservationType?): Pair<DateEvent?, DateEvent?> {
        val useful = events.filter { e ->
            val context = (lines.getOrNull(e.line - 1).orEmpty() + " " + lines[e.line]).lowercase()
            IGNORE_WORDS.none { it in context }
        }
        if (useful.isEmpty()) return null to null

        fun labeled(words: List<String>) = useful.firstOrNull { e ->
            val context = (lines.getOrNull(e.line - 1).orEmpty() + " " + lines[e.line]).lowercase()
            words.any { it in context }
        }

        val hasEnd = type?.hasEnd ?: true
        // Voo e trem: partida é o primeiro horário, chegada o seguinte.
        if (type == ReservationType.FLIGHT || type == ReservationType.TRAIN_BUS) {
            val timed = useful.filter { it.time != null }
            val start = timed.firstOrNull() ?: useful.first()
            val end = timed.firstOrNull { it.dateTime!! > start.dateTime!! && it !== start }
            return start to end
        }
        val start = labeled(START_WORDS.filterNot { it in END_WORDS }) ?: useful.first()
        val end = if (!hasEnd) null else labeled(END_WORDS)?.takeIf { it.date >= start.date && it !== start }
            ?: useful.lastOrNull { it.date > start.date }
        return start to end
    }

    // ---------- Detalhes por tipo ----------

    private val ROUTE = Regex("\\b([A-Z]{3})\\s*(?:→|->|–|—|-|>|/|\\bto\\b|\\bpara\\b|\\ba\\b)\\s*([A-Z]{3})\\b")
    private val AIRPORT_IN_PARENS = Regex("\\(([A-Z]{3})\\)")
    private val SEAT = Regex("(?i)(?:assento|seat|asiento|posto|si[èe]ge)\\s*[:nº°.]*\\s*(\\d{1,2}[A-K])\\b")
    private val GATE = Regex("(?i)(?:port[ãa]o|gate|puerta|porte|cancello)\\s*[:nº°.]*\\s*([A-Z]?\\d{1,3}[A-Z]?)\\b")
    private val TERMINAL = Regex("(?i)\\bterminal\\s*[:nº°.]*\\s*([A-Z0-9]{1,3})\\b")

    internal fun flightDetails(text: String): Map<ReservationField, String> {
        val result = mutableMapOf<ReservationField, String>()
        Airlines.findFlightNumber(text)?.let { (code, number) ->
            result[ReservationField.FLIGHT_NUMBER] = "$code$number"
            Airlines.nameOf(code)?.let { result[ReservationField.AIRLINE] = it }
        }
        val route = ROUTE.findAll(text).map { it.groupValues[1] to it.groupValues[2] }
            .firstOrNull { (a, b) -> a != b && Airports.isKnown(a) && Airports.isKnown(b) }
            ?: AIRPORT_IN_PARENS.findAll(text).map { it.groupValues[1] }.filter { Airports.isKnown(it) }.distinct().toList()
                .takeIf { it.size >= 2 }?.let { it[0] to it[1] }
        route?.let { (from, to) ->
            result[ReservationField.ORIGIN] = from
            result[ReservationField.DESTINATION] = to
        }
        SEAT.find(text)?.let { result[ReservationField.SEAT] = it.groupValues[1].uppercase() }
        GATE.find(text)?.let { result[ReservationField.GATE] = it.groupValues[1].uppercase() }
        TERMINAL.find(text)?.let { result[ReservationField.TERMINAL] = it.groupValues[1].uppercase() }
        return result
    }

    private val TRAIN_COMPANIES = listOf(
        "Trenitalia", "Italo", "SNCF", "OUIGO", "Renfe", "Iryo", "Eurostar", "Deutsche Bahn", "ÖBB", "SBB", "Trenord",
        "Comboios de Portugal", "FlixBus", "BlaBlaCar", "ALSA", "Cometa", "Viação 1001", "Águia Branca", "Catarinense",
        "Gontijo", "Itapemirim", "Buser", "Expresso do Sul", "Kaissara", "Real Expresso", "Penha", "Eurolines",
    )
    private val SERVICE = Regex("(?i)(?:trem|train|treno|tren|frecciarossa|frecciargento|italo|tgv|ave|ice|ônibus|bus|corsa|servi[çc]o)\\s*(?:n[º°o.]*)?\\s*:?\\s*(\\d{2,5})\\b")
    private val FROM = listOf("origem", "de", "from", "partenza da", "desde", "départ", "saindo de")
    private val TO = listOf("destino", "para", "to", "arrivo a", "hasta", "arrivée", "chegando em")
    private val COACH = Regex("(?i)(?:carro|vagão|coach|carriage|carrozza|coche|voiture)\\s*[:nº°.]*\\s*(\\d{1,2})\\D{0,20}?(?:assento|lugar|poltrona|seat|posto|asiento|place)\\s*[:nº°.]*\\s*(\\d{1,3}[A-D]?)")
    private val BUS_SEAT = Regex("(?i)(?:poltrona|assento|seat|posto|asiento)\\s*[:nº°.]*\\s*(\\d{1,3}[A-D]?)\\b")

    private fun trainDetails(text: String): Map<ReservationField, String> {
        val lines = text.lines()
        val result = mutableMapOf<ReservationField, String>()
        TRAIN_COMPANIES.firstOrNull { text.contains(it, ignoreCase = true) }?.let { result[ReservationField.COMPANY] = it }
        SERVICE.find(text)?.let { result[ReservationField.SERVICE_NUMBER] = it.groupValues[1] }
        labeled(lines, FROM)?.let { result[ReservationField.ORIGIN] = it.substringBefore(" - ").take(40) }
        labeled(lines, TO)?.let { result[ReservationField.DESTINATION] = it.substringBefore(" - ").take(40) }
        (COACH.find(text)?.let { "Carro ${it.groupValues[1]}, assento ${it.groupValues[2]}" }
            ?: BUS_SEAT.find(text)?.let { "Assento ${it.groupValues[1]}" })
            ?.let { result[ReservationField.SEAT_COACH] = it }
        return result
    }

    private val CAR_COMPANIES = listOf(
        "Localiza", "Movida", "Unidas", "Foco", "Hertz", "Avis", "Budget", "Europcar", "Sixt", "Enterprise", "Alamo",
        "National", "Thrifty", "Dollar", "Goldcar", "Rentcars", "Discover Cars", "Maggiore", "Locauto",
    )
    private val PICKUP_LABELS = listOf("local de retirada", "retirada", "pick-up location", "pickup location", "pick-up", "recogida", "ritiro", "prise en charge")
    private val DROPOFF_LABELS = listOf("local de devolução", "devolução", "drop-off location", "return location", "drop-off", "devolución", "riconsegna", "restitution")
    private val CAR_CATEGORY = Regex("(?i)(?:grupo|categoria|category|categoría|group|gruppo)\\s*[:]?\\s*([A-Z0-9][\\w -]{0,30})")

    private fun carDetails(lines: List<String>, text: String): Map<ReservationField, String> {
        val result = mutableMapOf<ReservationField, String>()
        CAR_COMPANIES.firstOrNull { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) }
            ?.let { result[ReservationField.COMPANY] = it }
        CAR_CATEGORY.find(text)?.let { result[ReservationField.CAR_CATEGORY] = it.groupValues[1].trim() }
        labeled(lines, PICKUP_LABELS, skipDates = true)?.let { result[ReservationField.PICKUP_LOCATION] = it }
        labeled(lines, DROPOFF_LABELS, skipDates = true)?.let { result[ReservationField.DROPOFF_LOCATION] = it }
        return result
    }

    private val PEOPLE = Regex("(?i)(\\d{1,2})\\s*(?:pessoas|people|persons|guests|personas|comensales|persone|coperti|couverts|pax)\\b")
    private val SECTOR_LABELS = listOf("setor", "sector", "settore", "secteur", "fila", "row")
    private val ROOM_LABELS = listOf("tipo de quarto", "quarto", "room type", "room", "habitación", "camera", "chambre", "acomodação")
    private val ADDRESS_LABELS = listOf("endereço", "address", "dirección", "indirizzo", "adresse", "localização", "location", "local")

    /**
     * Valor de um campo com rótulo: `Rótulo: valor` na mesma linha ou, se a linha tiver só o rótulo,
     * a linha seguinte.
     */
    private fun labeled(lines: List<String>, labels: List<String>, skipDates: Boolean = false): String? {
        for ((i, line) in lines.withIndex()) {
            val lower = line.lowercase()
            for (label in labels) {
                if (!lower.startsWith(label)) continue
                val rest = line.substring(label.length).trimStart(' ', ':', '-', '–', '\t').trim()
                val after = lower.getOrNull(label.length)
                // Rótulo precisa ser palavra inteira ("de" não casa com "Departure").
                if (after != null && after.isLetter()) continue
                val value = rest.ifBlank { lines.getOrNull(i + 1)?.trim().orEmpty() }
                    .let { if (skipDates) DateScanner.stripDates(it) else it }
                    .trim(' ', ',', '-', '·')
                if (value.length in 2..120) return value
            }
        }
        return null
    }

    // ---------- Título ----------

    private val LODGING_WORDS = Regex("(?i)\\b(hotel|hostel|pousada|resort|inn|suites?|apart|apartamento|apartment|residence|b&b|albergo|hôtel|palace|villa|casa)\\b")

    private fun title(lines: List<String>, type: ReservationType?, fileName: String): String {
        val fallback = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
        val candidate = when (type) {
            ReservationType.LODGING -> lines.firstOrNull { LODGING_WORDS.containsMatchIn(it) && it.length in 4..60 && !it.contains(':') && DateScanner.dates(it).isEmpty() }
            ReservationType.RESTAURANT -> lines.firstOrNull { it.contains("restaura", ignoreCase = true) && it.length in 4..60 && !it.contains(':') }
            else -> null
        }
        return (candidate ?: fallback).take(80)
    }
}

/** Uma data (e, se houver, um horário) encontrada numa linha do texto. */
internal data class DateEvent(val line: Int, val date: LocalDate, val time: LocalTime?) {
    val dateTime: LocalDateTime? get() = time?.let { date.atTime(it) }
}

internal object DateScanner {

    private val MONTHS: Map<String, Int> = buildMap {
        val names = listOf(
            listOf("janeiro", "january", "enero", "gennaio", "janvier", "jan", "ene", "gen", "janv"),
            listOf("fevereiro", "february", "febrero", "febbraio", "février", "fevrier", "fev", "feb", "fév", "févr"),
            listOf("março", "marco", "march", "marzo", "mars", "mar"),
            listOf("abril", "april", "aprile", "avril", "abr", "apr", "avr"),
            listOf("maio", "may", "mayo", "maggio", "mai", "mag"),
            listOf("junho", "june", "junio", "giugno", "juin", "jun", "giu"),
            listOf("julho", "july", "julio", "luglio", "juillet", "jul", "lug", "juil"),
            listOf("agosto", "august", "août", "aout", "ago", "aug"),
            listOf("setembro", "september", "septiembre", "settembre", "septembre", "set", "sep", "sept"),
            listOf("outubro", "october", "octubre", "ottobre", "octobre", "out", "oct", "ott"),
            listOf("novembro", "november", "noviembre", "novembre", "nov"),
            listOf("dezembro", "december", "diciembre", "dicembre", "décembre", "decembre", "dez", "dec", "dic", "déc"),
        )
        names.forEachIndexed { i, words -> words.forEach { put(it, i + 1) } }
    }

    /** Abreviações que também são palavras comuns: só valem com ano ("12 out 2026"). */
    private val AMBIGUOUS = setOf("out", "set", "mar", "may", "mai", "gen", "dec", "ago")

    private const val MONTH = "([A-Za-zÀ-ÿ]{3,10})"
    private val NUMERIC = Regex("\\b(\\d{1,2})[/.](\\d{1,2})[/.](\\d{2}|\\d{4})\\b")
    private val ISO = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
    private val DAY_MONTH = Regex("(?i)\\b(\\d{1,2})(?:º|°|er)?\\s*(?:de\\s+|of\\s+)?$MONTH\\.?(?:,?\\s*(?:de\\s+)?(\\d{4}|\\d{2}(?!\\s*:)))?\\b")
    private val MONTH_DAY = Regex("(?i)\\b$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b")
    private val TIME = Regex("(?i)\\b([01]?\\d|2[0-3])(?::|h)([0-5]\\d)(?:\\s*(am|pm|a\\.m\\.|p\\.m\\.))?\\b")

    /** Datas da linha, na ordem em que aparecem. */
    fun dates(line: String, today: LocalDate = LocalDate(2000, 1, 1)): List<Pair<IntRange, LocalDate>> {
        val found = mutableListOf<Pair<IntRange, LocalDate>>()
        fun add(range: IntRange, date: LocalDate?) {
            if (date != null && found.none { it.first.first <= range.last && range.first <= it.first.last }) found += range to date
        }
        ISO.findAll(line).forEach { m -> add(m.range, date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())) }
        NUMERIC.findAll(line).forEach { m ->
            add(m.range, date(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        MONTH_DAY.findAll(line).forEach { m ->
            val month = MONTHS[m.groupValues[1].lowercase()] ?: return@forEach
            add(m.range, date(m.groupValues[3].toInt(), month, m.groupValues[2].toInt()))
        }
        DAY_MONTH.findAll(line).forEach { m ->
            val word = m.groupValues[2].lowercase()
            val month = MONTHS[word] ?: return@forEach
            val yearText = m.groupValues[3]
            if (yearText.isEmpty() && word in AMBIGUOUS) return@forEach
            val day = m.groupValues[1].toInt()
            val date = if (yearText.isNotEmpty()) date(year(yearText), month, day) else nearestYear(month, day, today)
            add(m.range, date)
        }
        return found.sortedBy { it.first.first }
    }

    fun stripDates(line: String): String {
        var result = line
        dates(line).sortedByDescending { it.first.first }.forEach { (range, _) -> result = result.removeRange(range) }
        TIME.findAll(result).toList().asReversed().forEach { result = result.removeRange(it.range) }
        return result.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun times(line: String): List<Pair<IntRange, LocalTime>> = TIME.findAll(line).mapNotNull { m ->
        var hour = m.groupValues[1].toInt()
        val suffix = m.groupValues[3].lowercase().replace(".", "")
        if (suffix == "pm" && hour < 12) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        m.range to LocalTime(hour, m.groupValues[2].toInt())
    }.toList()

    /**
     * Junta datas e horários linha a linha. Horário sem data na linha vai para a última data vista
     * (até 3 linhas antes): é o caso de "Partida 10:35" logo abaixo de "12/10/2026".
     */
    fun events(lines: List<String>, today: LocalDate): List<DateEvent> {
        val events = mutableListOf<DateEvent>()
        var lastDateLine = -10
        var lastDate: LocalDate? = null
        lines.forEachIndexed { i, line ->
            val dates = dates(line, today)
            val times = times(line).filter { (range, _) -> dates.none { (d, _) -> d.first <= range.last && range.first <= d.last } }
            if (dates.isNotEmpty()) {
                dates.forEachIndexed { k, (_, date) -> events += DateEvent(i, date, times.getOrNull(k)?.second) }
                // Mais horários que datas: o resto fica na última data (ex.: "12/10 10:35 - 18:20").
                times.drop(dates.size).forEach { (_, t) -> events += DateEvent(i, dates.last().second, t) }
                lastDate = dates.last().second
                lastDateLine = i
            } else if (times.isNotEmpty() && lastDate != null && i - lastDateLine <= 3) {
                times.forEach { (_, t) ->
                    val pending = events.lastOrNull { it.date == lastDate && it.time == null }
                    if (pending != null) events[events.indexOf(pending)] = pending.copy(time = t, line = i)
                    else events += DateEvent(i, lastDate!!, t)
                }
            }
        }
        return events
    }

    private fun year(text: String) = if (text.length == 2) 2000 + text.toInt() else text.toInt()

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        if (year !in 2000..2100) null else runCatching { LocalDate(year, month, day) }.getOrNull()

    /** Sem ano: a ocorrência mais próxima a partir de 2 meses atrás. */
    private fun nearestYear(month: Int, day: Int, today: LocalDate): LocalDate? {
        val thisYear = date(today.year, month, day) ?: return null
        return if (thisYear.toEpochDays() < today.toEpochDays() - 60) date(today.year + 1, month, day) else thisYear
    }
}

internal object MoneyScanner {
    private val TOTAL_WORDS = listOf(
        "valor total", "total pago", "total a pagar", "preço total", "total geral", "total", "amount paid", "grand total",
        "total price", "total amount", "importe total", "precio total", "totale", "importo", "montant total", "montant", "valor pago",
    )
    private val AMOUNT = Regex(
        "(R\\$|US\\$|U\\$|€|£|\\$|\\b(?:BRL|EUR|USD|GBP|CHF|ARS|CLP|JPY)\\b)?\\s*" +
            "(\\d{1,3}(?:[.,  ]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*" +
            "(R\\$|€|£|\\b(?:BRL|EUR|USD|GBP|CHF|reais)\\b)?",
    )

    /** Valor total da reserva: o maior valor com moeda nas linhas de "total"; sem elas, nenhum. */
    fun total(lines: List<String>): Pair<Long, Currency>? {
        val candidates = mutableListOf<Pair<Long, Currency>>()
        lines.forEachIndexed { i, line ->
            val lower = line.lowercase()
            if (TOTAL_WORDS.none { lower.contains(it) } || lower.contains("subtotal")) return@forEachIndexed
            // Valor na mesma linha ou na seguinte (tabelas viram duas linhas no texto do PDF).
            val amounts = amounts(line).ifEmpty { lines.getOrNull(i + 1)?.let(::amounts).orEmpty() }
            candidates += amounts
        }
        return candidates.maxByOrNull { it.first }
    }

    fun amounts(line: String): List<Pair<Long, Currency>> = AMOUNT.findAll(line).mapNotNull { m ->
        val symbol = m.groupValues[1].ifEmpty { m.groupValues[3] }
        if (symbol.isEmpty()) return@mapNotNull null
        val cents = cents(m.groupValues[2]) ?: return@mapNotNull null
        cents to currency(symbol)
    }.filter { it.first > 0 }.toList()

    private fun currency(symbol: String): Currency = when (symbol.uppercase()) {
        "R$", "BRL", "REAIS" -> Currency.BRL
        "€", "EUR" -> Currency.EUR
        "£", "GBP" -> Currency.GBP
        "US$", "U$", "$", "USD" -> Currency.USD
        else -> Currency.of(symbol.uppercase())
    }

    /** `1.234,56`, `1,234.56`, `1 234,56` e `45` → centavos. */
    internal fun cents(text: String): Long? {
        val clean = text.replace(" ", "").replace(" ", "")
        val lastSep = clean.indexOfLast { it == ',' || it == '.' }
        val decimals = if (lastSep >= 0 && clean.length - lastSep - 1 in 1..2) clean.length - lastSep - 1 else 0
        val digits = clean.filter(Char::isDigit)
        val value = digits.toLongOrNull() ?: return null
        return when (decimals) {
            0 -> value * 100
            1 -> value * 10
            else -> value
        }
    }
}
