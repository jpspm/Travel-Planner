package com.jpspm.travelplanner.domain.repository

import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Algo que quero fazer na viagem, ainda sem dia (RF-36). */
data class Idea(
    val id: String,
    val tripId: String,
    val title: String,
    val notes: String,
    val place: Place?,
    /** Mesmas categorias do roteiro: `null` = atividade; senão, o tipo de reserva (ex.: ingresso). */
    val kind: ReservationType? = null,
)

data class IdeaInput(val title: String, val notes: String, val place: PlaceInput, val kind: ReservationType? = null)

interface IdeaRepository {
    fun observe(tripId: String): Flow<List<Idea>>
    suspend fun get(id: String): Idea?
    suspend fun save(tripId: String, id: String?, input: IdeaInput): String

    /**
     * Leva a ideia para o roteiro: vira uma atividade sem horário no [date], com o mesmo lugar,
     * e sai da lista de ideias. Retorna o id da atividade criada.
     */
    suspend fun schedule(id: String, date: LocalDate, timeZoneId: String): String?

    suspend fun delete(id: String)
}
