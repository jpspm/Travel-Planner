package com.jpspm.travelplanner.ui.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.domain.places.OpeningHours
import com.jpspm.travelplanner.resources.place_change
import com.jpspm.travelplanner.resources.place_opening_hours
import com.jpspm.travelplanner.resources.place_manual
import com.jpspm.travelplanner.resources.place_remove
import com.jpspm.travelplanner.resources.place_search_button
import com.jpspm.travelplanner.resources.place_search_on_map
import org.jetbrains.compose.resources.stringResource

/**
 * Local de uma atividade, reserva ou destino: busca com mapa e foto, ou nome e endereço à mão
 * quando não há internet. [searchQuery] pré-preenche a busca (ex.: título da atividade).
 */
@Composable
fun PlaceField(
    draft: PlaceDraft?,
    onChange: (PlaceDraft?) -> Unit,
    near: LatLng?,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val isManual = manual || (draft != null && draft.position == null)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            draft != null && !isManual -> PlaceCard(draft, onChange = { picking = true }, onRemove = { onChange(null) })
            isManual -> {
                ManualPlaceFields(
                    name = draft?.input?.name.orEmpty(),
                    address = draft?.input?.address.orEmpty(),
                    onChange = { name, address ->
                        onChange(if (name.isBlank() && address.isBlank()) null else PlaceDraft.manual(name, address, draft))
                    },
                )
                Row {
                    TextButton(onClick = { picking = true }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Text(stringResource(Res.string.place_search_on_map), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (draft != null) {
                        TextButton(onClick = { manual = false; onChange(null) }) { Text(stringResource(Res.string.place_remove)) }
                    }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picking = true }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(stringResource(Res.string.place_search_button), modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = { manual = true }) { Text(stringResource(Res.string.place_manual)) }
            }
        }
    }

    if (picking) {
        PlacePickerDialog(
            initialQuery = draft?.input?.name?.ifBlank { null } ?: searchQuery,
            near = draft?.position ?: near,
            onPick = { picked ->
                picking = false
                manual = false
                onChange(picked)
            },
            onManual = { picking = false; manual = true },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun PlaceCard(draft: PlaceDraft, onChange: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlacePhoto(draft.photoModel, size = 72)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(draft.input.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    draft.input.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                draft.input.openingHours?.let { hours ->
                    Text(
                        stringResource(Res.string.place_opening_hours, OpeningHours.describe(hours)),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                draft.photoCredit?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
            TextButton(onClick = onChange) { Text(stringResource(Res.string.place_change)) }
            TextButton(onClick = onRemove) { Text(stringResource(Res.string.place_remove)) }
        }
    }
}

/** Foto do lugar (arquivo local, URL ou arquivo da galeria) ou um ícone quando não há foto. */
@Composable
fun PlacePhoto(model: Any?, size: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(shape),
        )
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.size(size.dp)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null)
            }
        }
    }
}
