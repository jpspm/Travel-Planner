package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.cover_from_gallery
import com.jpspm.travelplanner.resources.cover_no_place_photos
import com.jpspm.travelplanner.resources.cover_places_header
import com.jpspm.travelplanner.resources.trip_menu_cover
import org.jetbrains.compose.resources.stringResource

/** Escolha da capa: uma foto dos lugares do roteiro ou uma imagem da galeria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverPickerSheet(
    places: List<Place>,
    onPlace: (Place) -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.trip_menu_cover), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (places.isEmpty()) Res.string.cover_no_place_photos else Res.string.cover_places_header),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (places.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(places, key = { it.id }) { place ->
                        Column(Modifier.clickable { onPlace(place) }) {
                            AsyncImage(
                                model = place.photoPath,
                                contentDescription = place.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                            )
                            Text(
                                place.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(Res.string.cover_from_gallery), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
