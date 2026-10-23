package com.jpspm.travelplanner.data.export

import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.data.backup.LocalBackupRepository
import com.jpspm.travelplanner.domain.agenda.AgendaBuilder
import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.city.CityGrouper
import com.jpspm.travelplanner.domain.city.place
import com.jpspm.travelplanner.domain.export.HtmlImages
import com.jpspm.travelplanner.domain.export.HtmlTexts
import com.jpspm.travelplanner.domain.export.TripCalendar
import com.jpspm.travelplanner.domain.export.TripHtml
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.MapScope
import com.jpspm.travelplanner.domain.repository.MapSnapshotRepository
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.agenda_no_time
import com.jpspm.travelplanner.resources.agenda_until
import com.jpspm.travelplanner.resources.html_credits_title
import com.jpspm.travelplanner.resources.html_day_map_alt
import com.jpspm.travelplanner.resources.html_generated_by
import com.jpspm.travelplanner.resources.html_map_credit
import com.jpspm.travelplanner.resources.html_photo_credit
import com.jpspm.travelplanner.resources.html_trip_map_alt
import com.jpspm.travelplanner.resources.itinerary_day_n
import com.jpspm.travelplanner.resources.itinerary_outside_trip
import com.jpspm.travelplanner.resources.trip_days
import com.jpspm.travelplanner.ui.components.endLabel
import com.jpspm.travelplanner.ui.components.label
import com.jpspm.travelplanner.ui.components.startLabel
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Monta o HTML do roteiro (RF-27 a RF-30) com as fotos reduzidas para manter o arquivo abaixo de
 * ~10 MB numa viagem típica (RNF-11) e grava num arquivo temporário para compartilhar.
 */
class HtmlExporter(
    private val trips: TripRepository,
    private val destinations: DestinationRepository,
    private val activities: ActivityRepository,
    private val reservations: ReservationRepository,
    private val snapshots: MapSnapshotRepository,
    private val clock: Clock,
    private val cacheDir: () -> PlatformFile = { FileKit.cacheDir },
) {

    suspend fun export(tripId: String): PlatformFile = withContext(Dispatchers.IO) {
        val trip = requireNotNull(trips.getTrip(tripId)) { "Viagem não encontrada" }
        val destinationList = destinations.observeDestinations(tripId).first()
        val days = AgendaBuilder.build(trip.days, activities.observeActivities(tripId).first(), reservations.observeReservations(tripId).first())
        val cities = CityGrouper.group(destinationList, days)
        val savedMaps = snapshots.observe(tripId).first()

        val places = days.flatMap { d -> d.items.mapNotNull { it.place() } }.filter { it.photoPath != null }.distinctBy { it.id }
        val images = HtmlImages(
            cover = trip.coverPhotoPath?.let { image(it, maxSide = 960) },
            tripMap = savedMaps.firstOrNull { it.scope == MapScope.TRIP }?.let { image(it.filePath, maxSide = null) },
            dayMaps = savedMaps.filter { it.scope == MapScope.DAY && it.date != null }
                .mapNotNull { s -> image(s.filePath, maxSide = null)?.let { s.date!! to it } }.toMap(),
            places = places.mapNotNull { p -> image(p.photoPath!!, maxSide = 360)?.let { p.id to it } }.toMap(),
        )

        val html = TripHtml.build(
            trip = trip,
            days = days,
            cities = cities.map { it.name },
            zoneNames = CityGrouper.zoneNames(cities),
            texts = texts(trip.days.size, days.map { (it.date.toEpochDays() - trip.startDate.toEpochDays()).toInt() + 1 }),
            images = images,
        )
        val file = cacheDir() / "roteiro-${LocalBackupRepository.slug(trip.name).ifBlank { "viagem" }}.html"
        file.writeString(html)
        file
    }

    /** Roteiro em `.ics` para importar no calendário (RF-46). */
    suspend fun exportCalendar(tripId: String): PlatformFile = withContext(Dispatchers.IO) {
        val trip = requireNotNull(trips.getTrip(tripId)) { "Viagem não encontrada" }
        val days = AgendaBuilder.build(trip.days, activities.observeActivities(tripId).first(), reservations.observeReservations(tripId).first())
        val typeLabels = ReservationType.entries.associateWith { getString(it.label) }
        val momentLabels = ReservationType.entries.associateWith { getString(it.startLabel) to getString(it.endLabel) }
        val labels = TripCalendar.Labels(
            moment = { item ->
                val (start, end) = momentLabels.getValue(item.reservation.type)
                if (item.role == AgendaItem.ReservationItem.Role.END) end else start
            },
            type = { typeLabels.getValue(it.reservation.type) },
        )
        val file = cacheDir() / "roteiro-${LocalBackupRepository.slug(trip.name).ifBlank { "viagem" }}.ics"
        file.writeString(TripCalendar.build(trip, days, labels, clock.now()))
        file
    }

    private suspend fun texts(dayCount: Int, dayNumbers: List<Int>): HtmlTexts {
        val dayTitles = dayNumbers.distinct().associateWith { getString(Res.string.itinerary_day_n, it) }
        val typeLabels = ReservationType.entries.associateWith { getString(it.label) }
        val startLabels = ReservationType.entries.associateWith { getString(it.startLabel) }
        val endLabels = ReservationType.entries.associateWith { getString(it.endLabel) }
        val untilTemplate = getString(Res.string.agenda_until, "\u0000")
        val photoTemplate = getString(Res.string.html_photo_credit, "\u0001", "\u0002")
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return HtmlTexts(
            dayCount = getPluralString(Res.plurals.trip_days, dayCount, dayCount),
            dayTitle = { dayTitles[it] ?: "" },
            outsideTrip = getString(Res.string.itinerary_outside_trip),
            noTime = getString(Res.string.agenda_no_time),
            until = { untilTemplate.replace("\u0000", it) },
            momentLabel = { item ->
                val map = if (item.role == AgendaItem.ReservationItem.Role.END) endLabels else startLabels
                map.getValue(item.reservation.type)
            },
            typeLabel = { typeLabels.getValue(it) },
            tripMapAlt = getString(Res.string.html_trip_map_alt),
            dayMapAlt = getString(Res.string.html_day_map_alt),
            creditsTitle = getString(Res.string.html_credits_title),
            photoCredit = { place, credit -> photoTemplate.replace("\u0001", place).replace("\u0002", credit) },
            mapCredit = getString(Res.string.html_map_credit),
            generatedBy = getString(Res.string.html_generated_by, "${TimeFormat.date(now.date)} ${TimeFormat.time(now.time)}"),
        )
    }

    /** Imagem do disco em `data:` URI; reduzida para [maxSide] px quando informado. */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun image(path: String, maxSide: Int?): String? = runCatching {
        val file = PlatformFile(path)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val jpeg = if (maxSide == null) bytes
        else FileKit.compressImage(bytes, ImageFormat.JPEG, quality = 78, maxWidth = maxSide, maxHeight = maxSide)
        "data:image/jpeg;base64," + Base64.encode(jpeg)
    }.getOrNull()
}
