package com.jpspm.travelplanner.domain.planning

import com.jpspm.travelplanner.core.geo.distanceKm
import com.jpspm.travelplanner.domain.repository.LatLng
import kotlin.math.roundToInt

/**
 * Distância e tempo a pé estimados entre dois lugares (RF-38), sem internet: linha reta × [STREET_FACTOR]
 * (as ruas não vão em linha reta) a [WALK_KMH]. É só uma estimativa e é sempre exibida como tal (RNF-25).
 */
data class WalkEstimate(val distanceKm: Double, val minutes: Int) {
    /** Acima disso, vale sugerir transporte em vez de ir a pé. */
    val isLong: Boolean get() = distanceKm > LONG_WALK_KM

    companion object {
        const val STREET_FACTOR = 1.3
        const val WALK_KMH = 5.0
        const val LONG_WALK_KM = 3.0
        /** Lugares a menos disso são considerados o mesmo ponto (sem estimativa). */
        const val SAME_PLACE_KM = 0.05

        fun between(a: LatLng, b: LatLng): WalkEstimate? {
            val km = distanceKm(a, b) * STREET_FACTOR
            if (km < SAME_PLACE_KM) return null
            return WalkEstimate(km, (km / WALK_KMH * 60).roundToInt().coerceAtLeast(1))
        }

        /** `850 m`, `1,2 km`, `12 km` */
        fun formatDistance(km: Double): String = when {
            km < 1 -> "${(km * 1000 / 10).roundToInt() * 10} m"
            km < 10 -> ((km * 10).roundToInt() / 10.0).toString().replace('.', ',') + " km"
            else -> "${km.roundToInt()} km"
        }

        /** `18 min`, `1 h 05 min` */
        fun formatMinutes(minutes: Int): String =
            if (minutes < 60) "$minutes min" else "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
    }
}
