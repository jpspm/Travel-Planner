package com.jpspm.travelplanner.domain.repository

/** Ponto geográfico em graus decimais. */
data class LatLng(val latitude: Double, val longitude: Double)

/** Resultado da busca de lugares (RF-10). */
data class PlaceCandidate(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String?,
    /** Tipo + ID do OpenStreetMap, ex.: `W215801333` (way), `N123` (node), `R1834818` (relation). */
    val osmId: String?,
    /** ID do Wikidata quando já veio na busca (Nominatim), ex.: `Q10285`. */
    val wikidata: String?,
    /** Cidade do lugar (ex.: `Roma`), usada para agrupar a viagem por cidade. */
    val city: String? = null,
    /** Horário `opening_hours` do OSM, quando veio na busca (Nominatim). */
    val openingHours: String? = null,
) {
    val position: LatLng get() = LatLng(latitude, longitude)
}

/** Foto ilustrativa da Wikimedia com os créditos exigidos pela licença (RF-11, RF-29). */
data class RemotePhoto(
    val url: String,
    val author: String,
    val license: String,
    val sourceUrl: String,
)

class PlaceSearchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Busca de lugares e fotos em APIs gratuitas (D6). Só estas operações usam a internet (RNF-01);
 * falhas de rede viram [PlaceSearchException].
 */
interface PlaceSearch {
    /** Sugestões enquanto o usuário digita (Photon). [near] dá preferência a resultados próximos. */
    suspend fun suggest(query: String, near: LatLng?): List<PlaceCandidate>

    /** Busca completa ao confirmar (Nominatim, nomes em pt-BR). No máximo 1 requisição por segundo. */
    suspend fun search(query: String, near: LatLng?): List<PlaceCandidate>

    /**
     * Foto (imagem do Wikidata ou, sem ela, da página da Wikipédia mais próxima) e horário de
     * funcionamento do lugar. Para resultados do Photon, uma consulta ao Nominatim traz os dois.
     */
    suspend fun details(candidate: PlaceCandidate): PlaceDetails

    /** Páginas da Wikipédia em português perto de [center], com foto quando houver (RF-42). */
    suspend fun nearby(center: LatLng, radiusMeters: Int = 1500): List<Attraction>

    /** Foto e créditos de um arquivo da Wikimedia Commons (ex.: a imagem de uma atração). */
    suspend fun photoForFile(fileName: String): RemotePhoto?

    /** Baixa a imagem (já redimensionada pelo servidor). */
    suspend fun download(url: String): ByteArray
}

data class PlaceDetails(val photo: RemotePhoto?, val openingHours: String?)

/** Atração perto de um lugar da viagem (RF-42): uma página da Wikipédia com coordenadas. */
data class Attraction(
    val title: String,
    val position: LatLng,
    val distanceMeters: Int,
    val thumbnailUrl: String?,
    /** Nome do arquivo da imagem na Commons, para buscar a foto com créditos ao salvar. */
    val imageFile: String?,
    val pageUrl: String?,
)
