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
        val photo = search.photoFor(candidate(wikidata = "Q10285"))!!
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
        val photo = search.photoFor(candidate(wikidata = null, osmId = null))
        assertEquals("CC BY-SA 4.0", photo?.license)
    }

    @Test
    fun noPhotoWhenNothingFound() = runTest {
        val (search, _) = searchWith { url -> if ("pt.wikipedia.org" in url) """{"batchcomplete":true}""" else null }
        assertNull(search.photoFor(candidate(wikidata = null, osmId = null)))
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
          "extratags":{"wikidata":"Q10285"}}]"""

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
