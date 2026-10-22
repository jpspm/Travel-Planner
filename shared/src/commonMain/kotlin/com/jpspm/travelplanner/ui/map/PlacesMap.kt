package com.jpspm.travelplanner.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.ui.navigation.LocalScreenSettled
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/**
 * Estilo vetorial gratuito e sem chave do OpenFreeMap, com dados do OpenStreetMap (D6). A atribuição
 * (© OpenStreetMap) fica visível no canto do mapa, como exige a política de uso (RNF-06).
 */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

data class MapMarker(
    val id: String,
    val position: LatLng,
    /** Texto curto dentro do pino (ex.: número da ordem no dia). */
    val badge: String,
    val description: String,
    val highlighted: Boolean = false,
)

/**
 * Mapa interativo com pinos (RF-14). Precisa de internet para carregar o mapa; sem rede, mostra
 * só os pinos sobre o fundo vazio. A câmera se ajusta sempre que o conjunto de pinos muda.
 */
@Composable
fun PlacesMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    onMarkerClick: (MapMarker) -> Unit = {},
    focus: LatLng? = null,
) {
    // Durante a animação de troca de tela, um fundo neutro no lugar do mapa (ver LocalScreenSettled).
    if (!LocalScreenSettled.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer))
        return
    }
    val initial = focus ?: markers.firstOrNull()?.position
    val state = rememberMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
        initialCameraPosition = initial?.let { CameraPosition(center = it.toPosition(), zoom = 13.0) } ?: CameraPosition(),
    )

    // Reajusta a câmera quando os pinos (ou o ponto em foco) mudam.
    LaunchedEffect(markers.map { it.id to it.position }, focus) {
        runCatching {
            when {
                focus != null -> state.animateCamera(CameraUpdate(center = focus.toPosition(), zoom = 15.0))
                markers.size == 1 -> state.animateCamera(CameraUpdate(center = markers.single().position.toPosition(), zoom = 14.0))
                markers.size > 1 -> state.animateCameraToBounds(
                    boundsOf(markers.map { it.position }),
                    // Margem maior embaixo: a faixa de créditos do mapa fica sobre a borda inferior.
                    CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 80.dp)),
                )
            }
        }
    }

    MaplibreMap(modifier = modifier, state = state) {
        include(MapOverlay.Default)
        markers.forEach { marker ->
            MarkerPin(
                marker = marker,
                onClick = { onMarkerClick(marker) },
                modifier = Modifier.placedAt(marker.position.toPosition(), Alignment.Center),
            )
        }
    }
}

@Composable
private fun MarkerPin(marker: MapMarker, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (marker.highlighted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val onColor = if (marker.highlighted) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
    // Área de toque de 48 dp em volta de um pino de 30 dp (RNF-18).
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = marker.description },
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            shadowElevation = 3.dp,
            modifier = Modifier.size(30.dp).border(2.dp, Color.White, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(marker.badge, color = onColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun LatLng.toPosition(): Position = Position(longitude = longitude, latitude = latitude)

private fun boundsOf(points: List<LatLng>): BoundingBox = BoundingBox(
    west = points.minOf { it.longitude },
    south = points.minOf { it.latitude },
    east = points.maxOf { it.longitude },
    north = points.maxOf { it.latitude },
)
