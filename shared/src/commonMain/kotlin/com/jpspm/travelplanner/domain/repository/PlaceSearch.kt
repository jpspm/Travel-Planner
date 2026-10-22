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

    /** Foto do lugar: imagem do Wikidata ou, sem ela, da página da Wikipédia mais próxima. */
    suspend fun photoFor(candidate: PlaceCandidate): RemotePhoto?

    /** Baixa a imagem (já redimensionada pelo servidor). */
    suspend fun download(url: String): ByteArray
}
