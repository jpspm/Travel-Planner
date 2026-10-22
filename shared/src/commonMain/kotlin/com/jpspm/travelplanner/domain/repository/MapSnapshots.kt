package com.jpspm.travelplanner.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

enum class MapScope { TRIP, DAY }

/** Imagem estática do mapa guardada no aparelho (RF-15): usada sem internet (RNF-02) e na exportação HTML. */
data class MapSnapshot(
    val id: String,
    val tripId: String,
    val scope: MapScope,
    /** Dia da imagem quando [scope] = [MapScope.DAY]. */
    val date: LocalDate?,
    val filePath: String,
    val signature: String,
)

interface MapSnapshotRepository {
    fun observe(tripId: String): Flow<List<MapSnapshot>>

    /** Grava (ou troca) a imagem do escopo/dia, apagando o arquivo anterior. */
    suspend fun save(tripId: String, scope: MapScope, date: LocalDate?, signature: String, jpeg: ByteArray)

    /** Remove imagens de dias que não têm mais lugares no mapa. */
    suspend fun deleteExcept(tripId: String, keep: Set<Pair<MapScope, LocalDate?>>)
}
