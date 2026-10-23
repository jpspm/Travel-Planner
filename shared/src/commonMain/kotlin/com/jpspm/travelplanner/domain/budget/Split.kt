package com.jpspm.travelplanner.domain.budget

import kotlin.math.abs
import kotlin.math.min

data class Traveler(val id: String, val tripId: String, val name: String)

/** Quanto um viajante pagou, quanto lhe cabe e o saldo (positivo = tem a receber). */
data class Balance(val traveler: Traveler, val paidCents: Long, val shareCents: Long) {
    val netCents: Long get() = paidCents - shareCents
}

/** Pagamento para acertar as contas: [from] paga [amountCents] para [to]. */
data class Transfer(val from: Traveler, val to: Traveler, val amountCents: Long)

data class Settlement(val balances: List<Balance>, val transfers: List<Transfer>)

/**
 * Divisão de gastos (RF-49). Cada gasto avulso tem quem pagou e entre quem dividir (vazio = todos);
 * o valor pago de uma reserva conta como pago pelo primeiro viajante e dividido entre todos.
 * Valores em BRL, divididos em partes iguais; o centavo que sobra fica com os primeiros da divisão.
 */
object SplitCalculator {

    fun settle(travelers: List<Traveler>, expenses: List<Expense>, reservationCents: List<Long>): Settlement {
        if (travelers.size < 2) return Settlement(travelers.map { Balance(it, 0, 0) }, emptyList())
        val ids = travelers.map { it.id }
        val paid = ids.associateWith { 0L }.toMutableMap()
        val share = ids.associateWith { 0L }.toMutableMap()

        fun add(amount: Long, payer: String?, among: List<String>) {
            val payerId = payer?.takeIf { it in ids } ?: ids.first()
            val group = among.filter { it in ids }.ifEmpty { ids }
            paid[payerId] = paid.getValue(payerId) + amount
            val each = amount / group.size
            val remainder = amount % group.size
            group.forEachIndexed { i, id -> share[id] = share.getValue(id) + each + if (i < remainder) 1 else 0 }
        }

        expenses.forEach { add(it.amountCents, it.paidBy, it.splitAmong) }
        reservationCents.filter { it > 0 }.forEach { add(it, null, emptyList()) }

        val balances = travelers.map { Balance(it, paid.getValue(it.id), share.getValue(it.id)) }
        return Settlement(balances, transfers(balances))
    }

    /** Acerto com poucas transferências: quem mais deve paga quem mais tem a receber, até zerar. */
    private fun transfers(balances: List<Balance>): List<Transfer> {
        val debtors = balances.filter { it.netCents < 0 }.map { it.traveler to -it.netCents }.toMutableList()
        val creditors = balances.filter { it.netCents > 0 }.map { it.traveler to it.netCents }.toMutableList()
        val result = mutableListOf<Transfer>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortByDescending { it.second }
            creditors.sortByDescending { it.second }
            val (debtor, owes) = debtors.first()
            val (creditor, due) = creditors.first()
            val amount = min(owes, due)
            if (amount > 0) result += Transfer(debtor, creditor, amount)
            debtors[0] = debtor to owes - amount
            creditors[0] = creditor to due - amount
            debtors.removeAll { abs(it.second) == 0L }
            creditors.removeAll { abs(it.second) == 0L }
        }
        return result
    }
}
