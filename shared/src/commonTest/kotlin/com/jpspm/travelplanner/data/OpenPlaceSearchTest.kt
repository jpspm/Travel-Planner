package com.jpspm.travelplanner.data

import com.jpspm.travelplanner.data.remote.OpenPlaceSearch
import com.jpspm.travelplanner.data.remote.USER_AGENT
import com.jpspm.travelplanner.data.remote.stripHtml
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.PlaceCandidate
import com.jpspm.travelplanner.domain.repository.PlaceSearchException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenPlaceSearchTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun searchWith(handler: (String) -> String?): Pair<OpenPlaceSearch, MutableList<String>> {
        val requested = mutableListOf<String>()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requested += url
            assertEquals(USER_AGENT, request.headers[HttpHeaders.UserAgent])
            val body = handler(url)
            if (body == null) respond("", HttpStatusCode.NotFound) else respond(body, HttpStatusCode.OK, json)
        }
        val client = HttpClient(engine) { defaultRequest { headers.append(HttpHeaders.UserAgent, USER_AGENT) } }
        return OpenPlaceSearch(client) to requested
    }

    @Test
    fun photonSuggestions() = runTest {
        val (search, requested) = searchWith { PHOTON }
        val results = search.suggest("Colosseo", LatLng(41.89, 12.49))
        assertEquals(1, results.size)
        with(results.single()) {
            assertEquals("Colosseo", name)
            assertEquals("Piazza del Colosseo, Celio, Roma, Lazio, Italia", address)
            assertEquals(41.8902, latitude, 1e-4)
            assertEquals(12.4924, longitude, 1e-4)
            assertEquals("IT", countryCode)
            assertEquals("W215801333", osmId)
        }
        assertTrue(requested.single().contains("lat=41.89"))
    }

    @Test
    fun nominatimSearchInPortuguese() = runTest {
        val (search, requested) = searchWith { NOMINATIM }
        val result = search.search("Coliseu", null).single()
        assertEquals("Coliseu", result.name)
        assertEquals("Piazza del Colosseo, Celio, Roma, Lácio, Itália", result.address)
        assertEquals("Q10285", result.wikidata)
        assertEquals("W215801333", result.osmId)
        assertTrue(requested.single().contains("accept-language=pt-BR"))
    }

    @Test
    fun photoFromWikidataAndCommons() = runTest {
        val (search, requested) = searchWith { url ->
            when {
                "wikidata.org" in url -> WIKIDATA
                "commons.wikimedia.org" in url -> COMMONS
                else -> null
            }
        }
        val photo = search.details(candidate(wikidata = "Q10285")).photo!!
        assertEquals("https://upload.wikimedia.org/thumb/1280px-Colosseo_2020.jpg", photo.url)
        assertEquals("Diliff", photo.author)
        assertEquals("CC BY-SA 4.0", photo.license)
        assertEquals("https://commons.wikimedia.org/wiki/File:Colosseo_2020.jpg", photo.sourceUrl)
        assertEquals(2, requested.size)
    }

    @Test
    fun photoFallsBackToNearbyWikipediaPage() = runTest {
        val (search, _) = searchWith { url ->
            when {
                "pt.wikipedia.org" in url -> GEOSEARCH
                "commons.wikimedia.org" in url -> COMMONS
                else -> null
            }
        }
        // Sem wikidata e sem osmId: vai direto para a geobusca na Wikipédia.
        val photo = search.details(candidate(wikidata = null, osmId = null)).photo
        assertEquals("CC BY-SA 4.0", photo?.license)
    }

    @Test
    fun noPhotoWhenNothingFound() = runTest {
        val (search, _) = searchWith { url -> if ("pt.wikipedia.org" in url) """{"batchcomplete":true}""" else null }
        assertNull(search.details(candidate(wikidata = null, osmId = null)).photo)
    }

    @Test
    fun openingHoursFromNominatimSearch() = runTest {
        val (search, _) = searchWith { NOMINATIM }
        assertEquals("Mo-Su 08:30-19:00", search.search("Coliseu", null).single().openingHours)
    }

    @Test
    fun photonResultGetsWikidataAndOpeningHoursInOneLookup() = runTest {
        val (search, requested) = searchWith { url ->
            when {
                "nominatim.openstreetmap.org/lookup" in url -> LOOKUP
                "wikidata.org" in url -> WIKIDATA
                "commons.wikimedia.org" in url -> COMMONS
                else -> null
            }
        }
        val details = search.details(candidate(wikidata = null))
        assertEquals("Mo-Fr 09:00-18:00; Su off", details.openingHours)
        assertEquals("Diliff", details.photo?.author)
        assertEquals(1, requested.count { "nominatim" in it }, "uma consulta só ao Nominatim")
    }

    @Test
    fun nearbyAttractionsSortedByDistance() = runTest {
        val (search, requested) = searchWith { NEARBY }
        val list = search.nearby(LatLng(41.8902, 12.4924))
        assertEquals(listOf("Arco de Constantino", "Fórum Romano"), list.map { it.title })
        assertTrue(list[0].distanceMeters < list[1].distanceMeters)
        assertEquals("https://upload.wikimedia.org/arco.jpg", list[0].thumbnailUrl)
        assertEquals("Arco.jpg", list[0].imageFile)
        assertTrue("ggscoord=41.8902%7C12.4924" in requested.single() || "ggscoord=41.8902|12.4924" in requested.single())
    }

    @Test
    fun httpErrorBecomesPlaceSearchException() = runTest {
        val (search, _) = searchWith { null }
        assertFailsWith<PlaceSearchException> { search.suggest("x y z", null) }
    }

    @Test
    fun stripsCommonsHtmlCredits() {
        assertEquals("Diliff", stripHtml("""<a href="//commons.wikimedia.org/wiki/User:Diliff">Diliff</a>"""))
        assertEquals("A & B", stripHtml("<span>A &amp; B</span>"))
    }

    private fun candidate(wikidata: String?, osmId: String? = "W215801333") =
        PlaceCandidate("Coliseu", "", 41.8902, 12.4924, "IT", osmId, wikidata)

    private companion object {
        const val PHOTON = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"osm_type":"W","osm_id":215801333,"name":"Colosseo","street":"Piazza del Colosseo",
           "district":"Celio","city":"Roma","state":"Lazio","country":"Italia","countrycode":"IT"},
           "geometry":{"type":"Point","coordinates":[12.4924,41.8902]}},
          {"type":"Feature","properties":{"osm_type":"W","osm_id":215801333,"name":"Colosseo","countrycode":"IT"},
           "geometry":{"type":"Point","coordinates":[12.4924,41.8902]}}
        ]}"""

        const val NOMINATIM = """
        [{"osm_type":"way","osm_id":215801333,"lat":"41.8902","lon":"12.4924","name":"Coliseu",
          "display_name":"Coliseu, Piazza del Colosseo, Roma",
          "address":{"road":"Piazza del Colosseo","suburb":"Celio","city":"Roma","state":"Lácio","country":"Itália","country_code":"it"},
          "extratags":{"wikidata":"Q10285","opening_hours":"Mo-Su 08:30-19:00"}}]"""

        const val LOOKUP = """[{"osm_type":"way","osm_id":215801333,"extratags":{"wikidata":"Q10285","opening_hours":"Mo-Fr 09:00-18:00; Su off"}}]"""

        const val NEARBY = """
        {"batchcomplete":true,"query":{"pages":[
          {"pageid":3,"title":"Fórum Romano","coordinates":[{"lat":41.8925,"lon":12.4853}],"fullurl":"https://pt.wikipedia.org/wiki/F%C3%B3rum_Romano"},
          {"pageid":1,"title":"Arco de Constantino","coordinates":[{"lat":41.8898,"lon":12.4906}],
           "thumbnail":{"source":"https://upload.wikimedia.org/arco.jpg","width":240,"height":160},"pageimage":"Arco.jpg",
           "fullurl":"https://pt.wikipedia.org/wiki/Arco_de_Constantino"},
          {"pageid":2,"title":"Sem coordenadas"}]}}"""

        const val WIKIDATA = """
        {"claims":{"P18":[{"mainsnak":{"datavalue":{"value":"Colosseo 2020.jpg","type":"string"}}}]}}"""

        const val COMMONS = """
        {"batchcomplete":true,"query":{"pages":[{"title":"File:Colosseo 2020.jpg","imageinfo":[{
          "thumburl":"https://upload.wikimedia.org/thumb/1280px-Colosseo_2020.jpg",
          "url":"https://upload.wikimedia.org/Colosseo_2020.jpg",
          "descriptionurl":"https://commons.wikimedia.org/wiki/File:Colosseo_2020.jpg",
          "extmetadata":{"Artist":{"value":"<a href=\"//commons.wikimedia.org/wiki/User:Diliff\">Diliff</a>"},
                         "LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}]}}"""

        const val GEOSEARCH = """
        {"batchcomplete":true,"query":{"pages":[
          {"pageid":1,"title":"Arco de Constantino","index":1,"pageimage":"Arco.jpg"},
          {"pageid":2,"title":"Coliseu","index":0,"pageimage":"Colosseo_2020.jpg"}]}}"""
    }
}
