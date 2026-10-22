package com.jpspm.travelplanner.core

import com.jpspm.travelplanner.core.geo.WebMercator
import com.jpspm.travelplanner.domain.repository.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebMercatorTest {

    @Test
    fun projectsKnownPoints() {
        val (x0, y0) = WebMercator.project(LatLng(0.0, 0.0))
        assertEquals(256.0, x0, 1e-9)
        assertEquals(256.0, y0, 1e-9)
        val (x1, _) = WebMercator.project(LatLng(0.0, 180.0))
        assertEquals(512.0, x1, 1e-9)
    }

    @Test
    fun unprojectIsInverse() {
        val p = LatLng(41.8902, 12.4924)
        val (x, y) = WebMercator.project(p)
        val back = WebMercator.unproject(x, y)
        assertEquals(p.latitude, back.latitude, 1e-9)
        assertEquals(p.longitude, back.longitude, 1e-9)
    }

    @Test
    fun fitKeepsAllPointsInsideThePaddedImage() {
        val points = listOf(LatLng(41.8902, 12.4924), LatLng(41.9065, 12.4536), LatLng(41.9009, 12.4833))
        val w = 600.0
        val h = 380.0
        val pad = 40.0
        val frame = WebMercator.fit(points, w, h, pad)
        points.forEach { p ->
            val (x, y) = WebMercator.toImage(p, frame, w, h)
            assertTrue(x in (pad - 1e-6)..(w - pad + 1e-6) && y in (pad - 1e-6)..(h - pad + 1e-6), "($x, $y) fora da área")
        }
        // A dimensão mais "apertada" encosta na margem: o zoom é o maior possível.
        val xs = points.map { WebMercator.toImage(it, frame, w, h).first }
        val ys = points.map { WebMercator.toImage(it, frame, w, h).second }
        val usedX = xs.max() - xs.min()
        val usedY = ys.max() - ys.min()
        assertTrue(kotlin.math.abs(usedX - (w - 2 * pad)) < 1e-6 || kotlin.math.abs(usedY - (h - 2 * pad)) < 1e-6)
    }

    @Test
    fun singlePointUsesFixedZoomAndCenter() {
        val p = LatLng(-23.5614, -46.6559)
        val frame = WebMercator.fit(listOf(p), 600.0, 380.0, 40.0)
        assertEquals(15.0, frame.zoom)
        val (x, y) = WebMercator.toImage(p, frame, 600.0, 380.0)
        assertEquals(300.0, x, 1e-6)
        assertEquals(190.0, y, 1e-6)
    }

    @Test
    fun zoomIsCappedForVeryClosePoints() {
        val frame = WebMercator.fit(listOf(LatLng(41.89020, 12.49240), LatLng(41.89021, 12.49241)), 600.0, 380.0, 40.0)
        assertEquals(16.0, frame.zoom)
    }
}
