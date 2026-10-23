package com.jpspm.travelplanner.domain.importing

/** Siglas IATA das companhias mais comuns para quem viaja do Brasil. */
internal object Airlines {
    private val NAMES = mapOf(
        "LA" to "LATAM", "JJ" to "LATAM", "G3" to "GOL", "AD" to "Azul", "2Z" to "Voepass",
        "TP" to "TAP", "AF" to "Air France", "KL" to "KLM", "LH" to "Lufthansa", "LX" to "Swiss", "OS" to "Austrian",
        "SN" to "Brussels Airlines", "IB" to "Iberia", "UX" to "Air Europa", "VY" to "Vueling", "AZ" to "ITA Airways",
        "BA" to "British Airways", "EI" to "Aer Lingus", "FR" to "Ryanair", "U2" to "easyJet", "W6" to "Wizz Air",
        "SK" to "SAS", "AY" to "Finnair", "TK" to "Turkish Airlines", "EK" to "Emirates", "QR" to "Qatar Airways",
        "EY" to "Etihad", "ET" to "Ethiopian", "AT" to "Royal Air Maroc", "MS" to "EgyptAir", "SA" to "South African",
        "AA" to "American Airlines", "UA" to "United", "DL" to "Delta", "AC" to "Air Canada", "B6" to "JetBlue",
        "AS" to "Alaska Airlines", "WN" to "Southwest", "CM" to "Copa", "AV" to "Avianca", "AR" to "Aerolíneas Argentinas",
        "H2" to "SKY Airline", "JA" to "JetSMART", "AM" to "Aeroméxico", "Y4" to "Volaris", "4M" to "LATAM",
        "NH" to "ANA", "JL" to "Japan Airlines", "KE" to "Korean Air", "SQ" to "Singapore Airlines", "CX" to "Cathay Pacific",
        "QF" to "Qantas", "NZ" to "Air New Zealand", "CA" to "Air China", "MU" to "China Eastern", "AI" to "Air India",
        "LO" to "LOT", "OK" to "Czech Airlines", "A3" to "Aegean", "TO" to "Transavia", "HV" to "Transavia", "DY" to "Norwegian",
        "EW" to "Eurowings", "4U" to "Germanwings", "LG" to "Luxair", "FI" to "Icelandair", "LY" to "El Al", "SV" to "Saudia",
    )

    private val LABELED = Regex("(?i)(?:voo|flight|vuelo|volo|vol|flug)\\s*(?:n[º°o.]*\\s*)?:?\\s*([A-Z0-9]{2})\\s?(\\d{1,4})\\b")
    private val BARE = Regex("\\b([A-Z][A-Z0-9]|[0-9][A-Z])\\s?(\\d{2,4})\\b")

    fun nameOf(code: String): String? = NAMES[code.uppercase()]

    /** Sigla e número do voo: primeiro com rótulo ("Voo LA8084"), senão a primeira sigla conhecida. */
    fun findFlightNumber(text: String): Pair<String, String>? {
        LABELED.findAll(text).firstOrNull { it.groupValues[1].uppercase() in NAMES }?.let {
            return it.groupValues[1].uppercase() to it.groupValues[2]
        }
        return BARE.findAll(text).firstOrNull { it.groupValues[1] in NAMES }?.let { it.groupValues[1] to it.groupValues[2] }
    }
}

/** Aeroportos mais usados por quem viaja do Brasil, com o fuso IANA de cada um. */
internal object Airports {
    private val ZONES: Map<String, String> = buildMap {
        fun zone(zone: String, vararg codes: String) = codes.forEach { put(it, zone) }
        // Brasil
        zone("America/Sao_Paulo", "GRU", "CGH", "VCP", "GIG", "SDU", "CNF", "PLU", "BSB", "CWB", "POA", "FLN", "NVT", "JOI",
            "IGU", "SSA", "VIX", "GYN", "UDI", "RAO", "SJP", "LDB", "MGF", "XAP", "CXJ", "IOS")
        zone("America/Recife", "REC", "NAT", "JPA", "MCZ", "AJU", "FEN")
        zone("America/Fortaleza", "FOR", "SLZ", "THE", "JDO", "JJD")
        zone("America/Belem", "BEL", "MCP", "STM")
        zone("America/Manaus", "MAO")
        zone("America/Cuiaba", "CGB")
        zone("America/Campo_Grande", "CGR")
        zone("America/Porto_Velho", "PVH")
        zone("America/Rio_Branco", "RBR")
        zone("America/Bahia", "BPS")
        zone("America/Araguaina", "PMW")
        // América do Sul
        zone("America/Argentina/Buenos_Aires", "EZE", "AEP", "COR", "MDZ", "BRC", "USH", "FTE", "IGR", "SLA")
        zone("America/Santiago", "SCL", "PUQ", "CJC")
        zone("America/Montevideo", "MVD", "PDP")
        zone("America/Asuncion", "ASU")
        zone("America/Lima", "LIM", "CUZ", "AQP")
        zone("America/Bogota", "BOG", "MDE", "CTG", "CLO", "ADZ")
        zone("America/La_Paz", "LPB", "VVI", "UYU")
        zone("America/Guayaquil", "UIO", "GYE")
        zone("America/Caracas", "CCS")
        zone("America/Panama", "PTY")
        // América do Norte e Caribe
        zone("America/New_York", "JFK", "EWR", "LGA", "MIA", "FLL", "MCO", "TPA", "ATL", "BOS", "IAD", "DCA", "PHL", "CLT", "DTW")
        zone("America/Chicago", "ORD", "MDW", "DFW", "IAH", "MSP", "AUS", "MSY")
        zone("America/Denver", "DEN")
        zone("America/Phoenix", "PHX")
        zone("America/Los_Angeles", "LAX", "SFO", "SEA", "LAS", "SAN", "SJC")
        zone("America/Toronto", "YYZ", "YUL", "YOW")
        zone("America/Vancouver", "YVR")
        zone("America/Mexico_City", "MEX")
        zone("America/Cancun", "CUN")
        zone("America/Santo_Domingo", "PUJ", "SDQ")
        zone("America/Havana", "HAV")
        zone("America/Aruba", "AUA")
        zone("America/Curacao", "CUR")
        zone("America/Costa_Rica", "SJO")
        // Europa
        zone("Europe/Lisbon", "LIS", "OPO")
        zone("Atlantic/Madeira", "FNC")
        zone("Atlantic/Azores", "PDL")
        zone("Europe/Madrid", "MAD", "BCN", "AGP", "SVQ", "VLC", "PMI", "IBZ", "BIO")
        zone("Atlantic/Canary", "LPA", "TFS", "TFN")
        zone("Europe/Paris", "CDG", "ORY", "NCE", "LYS", "MRS", "BOD", "TLS")
        zone("Europe/London", "LHR", "LGW", "STN", "LTN", "LCY", "MAN", "EDI")
        zone("Europe/Dublin", "DUB")
        zone("Europe/Amsterdam", "AMS")
        zone("Europe/Brussels", "BRU", "CRL")
        zone("Europe/Berlin", "FRA", "MUC", "BER", "DUS", "HAM", "CGN", "STR")
        zone("Europe/Zurich", "ZRH", "GVA", "BSL")
        zone("Europe/Vienna", "VIE")
        zone("Europe/Rome", "FCO", "CIA", "MXP", "LIN", "BGY", "VCE", "FLR", "PSA", "NAP", "BLQ", "TRN", "CTA", "PMO", "BRI", "OLB", "CAG")
        zone("Europe/Athens", "ATH", "JTR", "JMK", "HER")
        zone("Europe/Istanbul", "IST", "SAW")
        zone("Europe/Prague", "PRG")
        zone("Europe/Budapest", "BUD")
        zone("Europe/Warsaw", "WAW", "KRK")
        zone("Europe/Copenhagen", "CPH")
        zone("Europe/Stockholm", "ARN")
        zone("Europe/Oslo", "OSL")
        zone("Europe/Helsinki", "HEL")
        zone("Atlantic/Reykjavik", "KEF")
        zone("Europe/Zagreb", "ZAG", "SPU", "DBV")
        zone("Europe/Malta", "MLA")
        // África, Oriente Médio, Ásia e Oceania
        zone("Africa/Casablanca", "CMN", "RAK")
        zone("Africa/Cairo", "CAI")
        zone("Africa/Johannesburg", "JNB", "CPT")
        zone("Africa/Addis_Ababa", "ADD")
        zone("Asia/Dubai", "DXB", "AUH")
        zone("Asia/Qatar", "DOH")
        zone("Asia/Jerusalem", "TLV")
        zone("Asia/Tokyo", "NRT", "HND", "KIX")
        zone("Asia/Seoul", "ICN")
        zone("Asia/Shanghai", "PEK", "PVG")
        zone("Asia/Hong_Kong", "HKG")
        zone("Asia/Singapore", "SIN")
        zone("Asia/Bangkok", "BKK", "HKT")
        zone("Asia/Kolkata", "DEL", "BOM")
        zone("Australia/Sydney", "SYD")
        zone("Australia/Melbourne", "MEL")
        zone("Pacific/Auckland", "AKL")
    }

    fun isKnown(code: String): Boolean = code.uppercase() in ZONES

    fun zoneOf(code: String): String? = ZONES[code.uppercase()]
}
