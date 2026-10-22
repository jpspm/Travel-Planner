package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.repository.MapScope
import com.jpspm.travelplanner.domain.repository.MapSnapshot
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.summary_activities
import com.jpspm.travelplanner.resources.summary_cities
import com.jpspm.travelplanner.resources.summary_days
import com.jpspm.travelplanner.resources.summary_photos
import com.jpspm.travelplanner.resources.summary_places
import com.jpspm.travelplanner.resources.summary_reservations
import com.jpspm.travelplanner.resources.summary_share_html
import com.jpspm.travelplanner.resources.summary_spent
import com.jpspm.travelplanner.resources.summary_spent_of
import com.jpspm.travelplanner.ui.components.SectionHeader
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Aba Resumo das viagens passadas (RF-31): números, cidades, fotos e o roteiro para compartilhar. */
@Composable
fun SummaryTab(
    state: TripDetailUiState,
    snapshots: List<MapSnapshot>,
    onOpenCity: (String) -> Unit,
    onShareHtml: () -> Unit,
    exporting: Boolean,
    modifier: Modifier,
    budgetViewModel: BudgetViewModel = koinViewModel { parametersOf(state.trip!!.id) },
) {
    val trip = state.trip ?: return
    val budget by budgetViewModel.state.collectAsStateWithLifecycle()
    val items = state.days.flatMap { it.items }
    val places = items.mapNotNull { it.place() }.distinctBy { it.id }
    val photos = places.filter { it.photoPath != null }
    val hero = trip.coverPhotoPath ?: snapshots.firstOrNull { it.scope == MapScope.TRIP }?.filePath

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        hero?.let {
            item(key = "hero") {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
        }
        item(key = "stats") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
                modifier = Modifier.padding(16.dp),
            ) {
                Stat(trip.days.size.toString(), stringResource(Res.string.summary_days), Modifier.weight(1f))
                Stat(state.cities.size.toString(), stringResource(Res.string.summary_cities), Modifier.weight(1f))
                Stat(places.count { it.hasPosition }.toString(), stringResource(Res.string.summary_places), Modifier.weight(1f))
                Stat(items.count { it is AgendaItem.ActivityItem }.toString(), stringResource(Res.string.summary_activities), Modifier.weight(1f))
                Stat(state.reservations.size.toString(), stringResource(Res.string.summary_reservations), Modifier.weight(1f))
                Stat(
                    Money.format(budget.summary.spentTotalCents),
                    budget.summary.plannedTotalCents?.let { stringResource(Res.string.summary_spent_of, Money.format(it)) }
                        ?: stringResource(Res.string.summary_spent),
                    Modifier.weight(1f),
                )
            }
        }
        if (state.cities.isNotEmpty()) {
            item(key = "cities") {
                SectionHeader(stringResource(Res.string.summary_cities))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.cities.forEach { city -> AssistChip(onClick = { onOpenCity(city.key) }, label = { Text(city.name) }) }
                }
            }
        }
        if (photos.isNotEmpty()) {
            item(key = "photos") {
                SectionHeader(stringResource(Res.string.summary_photos), Modifier.padding(top = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { place ->
                        Column(Modifier.size(width = 120.dp, height = 150.dp)) {
                            AsyncImage(
                                model = place.photoPath,
                                contentDescription = place.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Text(place.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        item(key = "share") {
            Button(onClick = onShareHtml, enabled = !exporting, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text(stringResource(Res.string.summary_share_html), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
