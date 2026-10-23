package com.jpspm.travelplanner.data.remote

import com.jpspm.travelplanner.core.geo.distanceKm
import com.jpspm.travelplanner.domain.repository.Attraction
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.PlaceDetails
import com.jpspm.travelplanner.domain.repository.PlaceCandidate
import com.jpspm.travelplanner.domain.repository.PlaceSearch
import com.jpspm.travelplanner.domain.repository.PlaceSearchException
import com.jpspm.travelplanner.domain.repository.RemotePhoto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Busca de lugares e fotos no ecossistema OpenStreetMap/Wikimedia, respeitando as políticas de uso
 * (RNF-06): `User-Agent` identificando o app; Nominatim no máximo 1 req/s e só por ação do usuário;
 * sugestões ao digitar vão para o Photon, que permite esse uso.
 */
class OpenPlaceSearch(
    private val client: HttpClient = defaultHttpClient(),
    private val clock: Clock = Clock.System,
) : PlaceSearch {

    private val json = Json { ignoreUnknownKeys = true }
    private val nominatimLock = Mutex()
    private var lastNominatimCall = Instant.DISTANT_PAST

    override suspend fun suggest(query: String, near: LatLng?): List<PlaceCandidate> {
        val body = getText("https://photon.komoot.io/api/") {
            parameter("q", query)
            parameter("limit", 8)
            near?.let {
                parameter("lat", it.latitude)
                parameter("lon", it.longitude)
            }
        }
        return parsePhoton(body)
    }

    override suspend fun search(query: String, near: LatLng?): List<PlaceCandidate> {
        val body = nominatim("https://nominatim.openstreetmap.org/search") {
            parameter("q", query)
            parameter("format", "jsonv2")
            parameter("limit", 10)
            parameter("addressdetails", 1)
            parameter("extratags", 1)
            parameter("accept-language", "pt-BR")
            // Caixa de ~2° ao redor do destino: prioriza, mas não restringe, resultados próximos.
            near?.let {
                parameter("viewbox", "${it.longitude - 1},${it.latitude + 1},${it.longitude + 1},${it.latitude - 1}")
            }
        }
        return parseNominatim(body)
    }

    override suspend fun details(candidate: PlaceCandidate): PlaceDetails {
        // Resultados do Photon não trazem extratags: uma consulta ao Nominatim traz wikidata e horário juntos.
        val extratags = if (candidate.wikidata == null && candidate.openingHours == null) {
            candidate.osmId?.let { lookupExtratags(it) }
        } else null
        val wikidata = candidate.wikidata ?: extratags?.str("wikidata")
        val openingHours = candidate.openingHours ?: extratags?.str("opening_hours")
        val fileName = wikidata?.let { wikidataImage(it) } ?: nearbyWikipediaImage(candidate)
        return PlaceDetails(photo = fileName?.let { commonsImage(it) }, openingHours = openingHours)
    }

    override suspend fun photoForFile(fileName: String): RemotePhoto? = commonsImage(fileName)

    override suspend fun nearby(center: LatLng, radiusMeters: Int): List<Attraction> {
        val body = getText("https://pt.wikipedia.org/w/api.php") {
            parameter("action", "query")
            parameter("generator", "geosearch")
            parameter("ggscoord", "${center.latitude}|${center.longitude}")
            parameter("ggsradius", radiusMeters.coerceIn(10, 10_000))
            parameter("ggslimit", 30)
            parameter("prop", "pageimages|coordinates|info")
            parameter("piprop", "thumbnail|name")
            parameter("pithumbsize", 240)
            parameter("inprop", "url")
            parameter("format", "json")
            parameter("formatversion", 2)
        }
        return parseNearby(body, center)
    }

    internal fun parseNearby(body: String, center: LatLng): List<Attraction> =
        json.parseToJsonElement(body).jsonObject.obj("query")?.arr("pages").orEmpty().mapNotNull { element ->
            val page = element.jsonObject
            val coords = page.arr("coordinates")?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val lat = (coords["lat"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lon = (coords["lon"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val position = LatLng(lat, lon)
            Attraction(
                title = page.str("title") ?: return@mapNotNull null,
                position = position,
                distanceMeters = (distanceKm(center, position) * 1000).toInt(),
                thumbnailUrl = page.obj("thumbnail")?.str("source"),
                imageFile = page.str("pageimage"),
                pageUrl = page.str("fullurl"),
            )
        }.sortedBy { it.distanceMeters }

    override suspend fun download(url: String): ByteArray = request(url).bodyAsBytes()

    private suspend fun lookupExtratags(osmId: String): JsonObject? {
        val body = nominatim("https://nominatim.openstreetmap.org/lookup") {
            parameter("osm_ids", osmId)
            parameter("format", "jsonv2")
            parameter("extratags", 1)
        }
        return json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject?.obj("extratags")
    }

    private suspend fun wikidataImage(entity: String): String? {
        val body = getText("https://www.wikidata.org/w/api.php") {
            parameter("action", "wbgetclaims")
            parameter("entity", entity)
            parameter("property", "P18")
            parameter("format", "json")
        }
        return json.parseToJsonElement(body).jsonObject.obj("claims")
            ?.arr("P18")?.firstOrNull()?.jsonObject
            ?.obj("mainsnak")?.obj("datavalue")?.str("value")
    }

    /** Sem imagem no Wikidata: usa a página da Wikipédia em português mais próxima das coordenadas. */
    private suspend fun nearbyWikipediaImage(candidate: PlaceCandidate): String? {
        val body = getText("https://pt.wikipedia.org/w/api.php") {
            parameter("action", "query")
            parameter("generator", "geosearch")
            parameter("ggscoord", "${candidate.latitude}|${candidate.longitude}")
            parameter("ggsradius", 150)
            parameter("ggslimit", 5)
            parameter("prop", "pageimages")
            parameter("piprop", "name")
            parameter("format", "json")
            parameter("formatversion", 2)
        }
        val pages = json.parseToJsonElement(body).jsonObject.obj("query")?.arr("pages")
            ?.map { it.jsonObject }
            ?.filter { it.str("pageimage") != null }
            ?.sortedBy { (it["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
        // Prefere a página cujo título lembra o nome do lugar; senão, a mais próxima.
        val nameWords = candidate.name.lowercase().split(' ').filter { it.length > 3 }
        val match = pages.firstOrNull { page -> nameWords.any { page.str("title").orEmpty().lowercase().contains(it) } }
        return (match ?: pages.firstOrNull())?.str("pageimage")
    }

    private suspend fun commonsImage(fileName: String): RemotePhoto? {
        val body = getText("https://commons.wikimedia.org/w/api.php") {
            parameter("action", "query")
            parameter("titles", "File:$fileName")
            parameter("prop", "imageinfo")
            parameter("iiprop", "url|extmetadata")
            parameter("iiurlwidth", 1280)
            parameter("iiextmetadatafilter", "Artist|LicenseShortName")
            parameter("format", "json")
            parameter("formatversion", 2)
        }
        val info = json.parseToJsonElement(body).jsonObject.obj("query")?.arr("pages")
            ?.firstOrNull()?.jsonObject?.arr("imageinfo")?.firstOrNull()?.jsonObject ?: return null
        val metadata = info.obj("extmetadata")
        return RemotePhoto(
            url = info.str("thumburl") ?: info.str("url") ?: return null,
            author = metadata?.obj("Artist")?.str("value")?.let(::stripHtml).orEmpty().ifBlank { "Wikimedia Commons" },
            license = metadata?.obj("LicenseShortName")?.str("value").orEmpty(),
            sourceUrl = info.str("descriptionurl").orEmpty(),
        )
    }

    private suspend fun nominatim(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String =
        nominatimLock.withLock {
            val wait = lastNominatimCall + 1.1.seconds - clock.now()
            if (wait.isPositive()) delay(wait)
            try {
                getText(url, block)
            } finally {
                lastNominatimCall = clock.now()
            }
        }

    private suspend fun getText(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): String =
        request(url, block).bodyAsText()

    private suspend fun request(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val response = try {
            client.get(url, block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PlaceSearchException("Falha de rede: ${e.message}", e)
        }
        if (!response.status.isSuccess()) throw PlaceSearchException("HTTP ${response.status.value} em $url")
        return response
    }

    internal fun parsePhoton(body: String): List<PlaceCandidate> =
        json.parseToJsonElement(body).jsonObject.arr("features").orEmpty().mapNotNull { feature ->
            val f = feature.jsonObject
            val p = f.obj("properties") ?: return@mapNotNull null
            val coords = f.obj("geometry")?.arr("coordinates") ?: return@mapNotNull null
            val street = listOfNotNull(p.str("street"), p.str("housenumber")).joinToString(" ").ifBlank { null }
            val name = p.str("name") ?: street ?: p.str("city") ?: return@mapNotNull null
            PlaceCandidate(
                name = name,
                address = listOfNotNull(street, p.str("district") ?: p.str("locality"), p.str("city"), p.str("state"), p.str("country"))
                    .distinct().filter { it != name }.joinToString(", "),
                latitude = coords.getOrNull(1)?.let { (it as JsonPrimitive).doubleOrNull } ?: return@mapNotNull null,
                longitude = coords.getOrNull(0)?.let { (it as JsonPrimitive).doubleOrNull } ?: return@mapNotNull null,
                countryCode = p.str("countrycode")?.uppercase(),
                osmId = p.str("osm_type")?.let { type -> p.str("osm_id")?.let { "$type$it" } },
                wikidata = null,
                city = p.str("city") ?: p.str("county"),
            )
        }.distinctBy { it.osmId ?: "${it.name}|${it.latitude}|${it.longitude}" }

    internal fun parseNominatim(body: String): List<PlaceCandidate> =
        json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val r = element.jsonObject
            val address = r.obj("address")
            val name = r.str("name")?.ifBlank { null } ?: r.str("display_name")?.substringBefore(',') ?: return@mapNotNull null
            val street = listOfNotNull(address?.str("road"), address?.str("house_number")).joinToString(" ").ifBlank { null }
            val city = address?.let { it.str("city") ?: it.str("town") ?: it.str("village") ?: it.str("municipality") }
            PlaceCandidate(
                name = name,
                address = listOfNotNull(street, address?.str("suburb"), city, address?.str("state"), address?.str("country"))
                    .distinct().filter { it != name }.joinToString(", "),
                latitude = r.str("lat")?.toDoubleOrNull() ?: return@mapNotNull null,
                longitude = r.str("lon")?.toDoubleOrNull() ?: return@mapNotNull null,
                countryCode = address?.str("country_code")?.uppercase(),
                osmId = r.str("osm_type")?.firstOrNull()?.uppercaseChar()?.let { type -> r.str("osm_id")?.let { "$type$it" } },
                wikidata = r.obj("extratags")?.str("wikidata"),
                city = city,
                openingHours = r.obj("extratags")?.str("opening_hours"),
            )
        }.distinctBy { it.osmId ?: "${it.name}|${it.latitude}|${it.longitude}" }
}

const val USER_AGENT = "TravelPlanner/1.0 (app pessoal de viagens; com.jpspm.travelplanner)"

fun defaultHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
    }
    defaultRequest { headers.append(HttpHeaders.UserAgent, USER_AGENT) }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Créditos da Commons vêm em HTML (`<a href=...>Autor</a>`); fica só o texto. */
internal fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
        .replace(Regex("\\s+"), " ").trim()
