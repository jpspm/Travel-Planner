package com.jpspm.travelplanner.core.time

import com.jpspm.travelplanner.core.geo.distanceKm

/**
 * Sugere o fuso IANA de um local, offline (RF-13, RNF-08). Usa a cidade de referência de cada fuso
 * (tabela `zone1970.tab`): entre os fusos do país, escolhe o mais próximo das coordenadas. Em países
 * com vários fusos isso é só uma sugestão; o usuário sempre pode trocar.
 */
object TimeZoneSuggester {

    private class Zone(val countries: Set<String>, val latitude: Double, val longitude: Double, val id: String)

    private val zones: List<Zone> by lazy {
        ZONE_TABLE.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val (lat, lon) = parseIso6709(parts[1]) ?: return@mapNotNull null
                Zone(parts[0].split(',').toSet(), lat, lon, parts[2].trim())
            }
            .toList()
    }

    /** Fusos possíveis para o país, em ordem alfabética. */
    fun zonesOf(countryCode: String): List<String> =
        zones.filter { countryCode.uppercase() in it.countries }.map { it.id }.sorted()

    fun suggest(countryCode: String?, latitude: Double?, longitude: Double?): String? {
        val candidates = countryCode?.uppercase()?.let { cc -> zones.filter { cc in it.countries } }.orEmpty()
            .ifEmpty { if (latitude != null && longitude != null) zones else emptyList() }
        if (candidates.isEmpty()) return null
        val best = if (candidates.size == 1 || latitude == null || longitude == null) {
            candidates.first()
        } else {
            candidates.minBy { distanceKm(latitude, longitude, it.latitude, it.longitude) }
        }
        return best.id.takeIf(::isValidTimeZoneId)
    }

    /** `+4230+00131` ou `-235320-0463800` → graus decimais. */
    internal fun parseIso6709(text: String): Pair<Double, Double>? {
        val split = text.indexOfAny(charArrayOf('+', '-'), startIndex = 1)
        if (split <= 0) return null
        val lat = parseCoordinate(text.substring(0, split), degreeDigits = 2) ?: return null
        val lon = parseCoordinate(text.substring(split), degreeDigits = 3) ?: return null
        return lat to lon
    }

    private fun parseCoordinate(text: String, degreeDigits: Int): Double? {
        val sign = if (text.startsWith('-')) -1 else 1
        val digits = text.drop(1)
        if (digits.length < degreeDigits + 2) return null
        val degrees = digits.substring(0, degreeDigits).toIntOrNull() ?: return null
        val minutes = digits.substring(degreeDigits, degreeDigits + 2).toIntOrNull() ?: return null
        val seconds = digits.substring(degreeDigits + 2).takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        return sign * (degrees + minutes / 60.0 + seconds / 3600.0)
    }
}
