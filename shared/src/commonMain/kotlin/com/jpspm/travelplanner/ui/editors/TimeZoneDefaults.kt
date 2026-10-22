package com.jpspm.travelplanner.ui.editors

import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.repository.LatLng
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Fuso sugerido para uma data: o do destino que cobre a data, senão o do primeiro destino, senão o do aparelho. */
fun defaultTimeZoneFor(date: LocalDate?, destinations: List<Destination>): String =
    destinations.firstOrNull { date != null && it.covers(date) }?.timeZoneId
        ?: destinations.firstOrNull()?.timeZoneId
        ?: TimeZone.currentSystemDefault().id

/** Ponto de referência para a busca de lugares: o destino da data, senão qualquer destino com coordenadas. */
fun nearFor(date: LocalDate?, destinations: List<Destination>): LatLng? {
    fun Destination.position() = place?.let { p ->
        if (p.latitude != null && p.longitude != null) LatLng(p.latitude, p.longitude) else null
    }
    return destinations.firstOrNull { date != null && it.covers(date) }?.position()
        ?: destinations.firstNotNullOfOrNull { it.position() }
}

/** Sugestões para o seletor de fuso: fusos dos destinos da viagem e o do aparelho. */
fun timeZoneSuggestions(destinations: List<Destination>): List<String> =
    (destinations.map { it.timeZoneId } + TimeZone.currentSystemDefault().id).distinct()
