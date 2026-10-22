package com.jpspm.travelplanner.core.geo

import com.jpspm.travelplanner.domain.repository.LatLng
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Distância em km entre dois pontos (fórmula de haversine). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    fun rad(d: Double) = d * PI / 180
    val dLat = rad(lat2 - lat1)
    val dLon = rad(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
    return 2 * 6371 * asin(sqrt(a))
}

fun distanceKm(a: LatLng, b: LatLng): Double = distanceKm(a.latitude, a.longitude, b.latitude, b.longitude)
