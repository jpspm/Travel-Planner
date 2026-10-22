package com.jpspm.travelplanner.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpspm.travelplanner.core.geo.WebMercator
import com.jpspm.travelplanner.domain.agenda.AgendaDay
import com.jpspm.travelplanner.domain.map.StaticMapSpec
import com.jpspm.travelplanner.domain.map.StaticMapSpecs
import com.jpspm.travelplanner.domain.repository.MapSnapshotRepository
import com.jpspm.travelplanner.platform.Connectivity
import com.jpspm.travelplanner.platform.encodeJpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapSnapshotter
import org.maplibre.compose.style.BaseStyle

/** Tamanho da imagem estática em dp e densidade de renderização (≈ 1080 × 680 px, ~100 KB em JPEG). */
private const val WIDTH_DP = 600.0
private const val HEIGHT_DP = 380.0
private const val PADDING_DP = 44.0
private const val RENDER_DENSITY = 1.8f

private val PIN_COLOR = Color(0xFF00658E)

/**
 * Mantém as imagens estáticas do mapa da viagem atualizadas (RF-15). Roda enquanto a tela da viagem
 * está aberta e há internet; só refaz as imagens cujos lugares mudaram, o que mantém o uso de tiles
 * baixo, como pede a política do OpenFreeMap/OSM (RNF-06).
 */
@Composable
fun StaticMapSync(tripId: String, tripStart: LocalDate, days: List<AgendaDay>) {
    val repository = koinInject<MapSnapshotRepository>()
    val online by koinInject<Connectivity>().online.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()
    val specs = StaticMapSpecs.build(tripStart, days)

    LaunchedEffect(tripId, specs.map { it.signature }, online) {
        if (!online) return@LaunchedEffect
        delay(1_500) // espera edições em sequência assentarem
        repository.deleteExcept(tripId, specs.map { it.scope to it.date }.toSet())
        val saved = repository.observe(tripId).first()
        val pending = specs.filter { spec ->
            saved.none { it.scope == spec.scope && it.date == spec.date && it.signature == spec.signature }
        }
        if (pending.isEmpty()) return@LaunchedEffect
        val snapshotter = DefaultMapRuntime.instance.createSnapshotter(BaseStyle.Uri(MAP_STYLE_URL))
        try {
            for (spec in pending) {
                try {
                    val jpeg = render(snapshotter, spec, textMeasurer)
                    repository.save(tripId, spec.scope, spec.date, spec.signature, jpeg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sem rede no meio do caminho ou tile que falhou: tenta de novo na próxima vez.
                }
            }
        } finally {
            snapshotter.close()
        }
    }
}

private suspend fun render(snapshotter: MapSnapshotter, spec: StaticMapSpec, textMeasurer: TextMeasurer): ByteArray {
    val frame = WebMercator.fit(spec.pins.map { it.position }, WIDTH_DP, HEIGHT_DP, PADDING_DP)
    val density = Density(RENDER_DENSITY)
    val map = snapshotter.capture(DpSize(WIDTH_DP.dp, HEIGHT_DP.dp)) {
        cameraPosition = CameraPosition(center = frame.center.toPosition(), zoom = frame.zoom)
        this.density = density
    }

    // Copia a imagem do mapa e desenha por cima os pinos numerados e a atribuição do mapa.
    val output = ImageBitmap(map.width, map.height)
    val scale = map.width / WIDTH_DP.toFloat()
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(output), Size(map.width.toFloat(), map.height.toFloat())) {
        drawImage(map)
        spec.pins.forEach { pin ->
            val (x, y) = WebMercator.toImage(pin.position, frame, WIDTH_DP, HEIGHT_DP)
            val center = Offset(x.toFloat() * scale, y.toFloat() * scale)
            drawCircle(Color.White, radius = 17.dp.toPx(), center = center)
            drawCircle(PIN_COLOR, radius = 14.dp.toPx(), center = center)
            val label = textMeasurer.measure(pin.badge, TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold), density = density)
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f))
        }
        val credit = textMeasurer.measure(
            "© OpenStreetMap · OpenFreeMap",
            TextStyle(color = Color(0xFF333333), fontSize = 10.sp),
            density = density,
        )
        val margin = 4.dp.toPx()
        val topLeft = Offset(size.width - credit.size.width - 2 * margin, size.height - credit.size.height - 2 * margin)
        drawRect(Color.White.copy(alpha = 0.8f), topLeft = topLeft, size = Size(credit.size.width + 2 * margin, credit.size.height + 2 * margin))
        drawText(credit, topLeft = Offset(topLeft.x + margin, topLeft.y + margin))
    }
    return output.encodeJpeg(85)
}
