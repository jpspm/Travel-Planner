package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.city.CityGroup
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.cities_empty_message
import com.jpspm.travelplanner.resources.cities_empty_title
import com.jpspm.travelplanner.resources.city_activities
import com.jpspm.travelplanner.resources.city_auto
import com.jpspm.travelplanner.resources.city_lodging
import com.jpspm.travelplanner.resources.city_nights
import com.jpspm.travelplanner.resources.city_reservations
import com.jpspm.travelplanner.ui.components.EmptyState
import com.jpspm.travelplanner.ui.place.PlacePhoto
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Aba Cidades: um cartão por cidade, com foto, datas, hospedagem e o que acontece lá. */
@Composable
fun CitiesTab(cities: List<CityGroup>, onOpen: (CityGroup) -> Unit, modifier: Modifier) {
    if (cities.isEmpty()) {
        EmptyState(
            emoji = "🏙️",
            title = stringResource(Res.string.cities_empty_title),
            message = stringResource(Res.string.cities_empty_message),
            modifier = modifier,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), modifier = modifier) {
        items(cities, key = { it.key }) { city -> CityCard(city, onClick = { onOpen(city) }) }
    }
}

@Composable
private fun CityCard(city: CityGroup, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            PlacePhoto(city.photoPath, size = 84)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(city.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val start = city.startDate
                val end = city.endDate
                if (start != null && end != null) {
                    val nights = (end.toEpochDays() - start.toEpochDays()).toInt()
                    val nightsText = if (nights > 0) " · " + pluralStringResource(Res.plurals.city_nights, nights, nights) else ""
                    Text(TimeFormat.range(start, end) + nightsText, style = MaterialTheme.typography.bodyMedium)
                }
                city.lodgings.firstOrNull()?.let { hotel ->
                    Text(
                        stringResource(Res.string.city_lodging, hotel.title),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val counts = listOfNotNull(
                    city.activityCount.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.city_activities, it, it) },
                    city.reservationCount.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.city_reservations, it, it) },
                    stringResource(Res.string.city_auto).takeIf { !city.isManual },
                )
                if (counts.isNotEmpty()) {
                    Text(
                        counts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
