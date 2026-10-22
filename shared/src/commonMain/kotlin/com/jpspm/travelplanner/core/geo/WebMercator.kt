package com.jpspm.travelplanner.core.geo

import com.jpspm.travelplanner.domain.repository.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Projeção Web Mercator do MapLibre: no zoom 0 o mundo inteiro tem [TILE_SIZE] × [TILE_SIZE] unidades
 * (dp) e cada nível de zoom dobra esse tamanho. Usada para enquadrar a imagem estática do mapa e
 * posicionar os pinos sobre ela.
 */
object WebMercator {
    const val TILE_SIZE = 512.0
    private const val MAX_LATITUDE = 85.05112878

    /** Coordenadas no mundo no zoom 0, de 0 a [TILE_SIZE]. */
    fun project(point: LatLng): Pair<Double, Double> {
        val lat = point.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
        val x = (point.longitude + 180) / 360 * TILE_SIZE
        val y = (1 - ln(tan(lat) + 1 / kotlin.math.cos(lat)) / PI) / 2 * TILE_SIZE
        return x to y
    }

    fun unproject(x: Double, y: Double): LatLng {
        val lon = x / TILE_SIZE * 360 - 180
        val n = PI - 2 * PI * y / TILE_SIZE
        val lat = 180 / PI * atan(0.5 * (exp(n) - exp(-n)))
        return LatLng(lat, lon)
    }

    data class Frame(val center: LatLng, val zoom: Double)

    /**
     * Centro e zoom que enquadram [points] numa imagem de [width] × [height] dp com [padding] dp de
     * margem. Um ponto só usa [singleZoom]; o zoom nunca passa de [maxZoom].
     */
    fun fit(points: List<LatLng>, width: Double, height: Double, padding: Double, singleZoom: Double = 15.0, maxZoom: Double = 16.0): Frame {
        require(points.isNotEmpty())
        val projected = points.map(::project)
        val minX = projected.minOf { it.first }
        val maxX = projected.maxOf { it.first }
        val minY = projected.minOf { it.second }
        val maxY = projected.maxOf { it.second }
        val center = unproject((minX + maxX) / 2, (minY + maxY) / 2)
        if (points.size == 1 || (maxX - minX < 1e-9 && maxY - minY < 1e-9)) return Frame(center, singleZoom)
        val scaleX = (width - 2 * padding) / (maxX - minX).coerceAtLeast(1e-9)
        val scaleY = (height - 2 * padding) / (maxY - minY).coerceAtLeast(1e-9)
        return Frame(center, log2(min(scaleX, scaleY)).coerceAtMost(maxZoom))
    }

    /** Posição em dp de [point] numa imagem de [width] × [height] dp centrada em [frame]. */
    fun toImage(point: LatLng, frame: Frame, width: Double, height: Double): Pair<Double, Double> {
        val scale = 2.0.pow(frame.zoom)
        val (px, py) = project(point)
        val (cx, cy) = project(frame.center)
        return (px - cx) * scale + width / 2 to (py - cy) * scale + height / 2
    }
}
