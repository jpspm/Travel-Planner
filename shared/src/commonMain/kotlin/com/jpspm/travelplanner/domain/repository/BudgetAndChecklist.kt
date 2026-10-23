package com.jpspm.travelplanner.domain.repository

import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import com.jpspm.travelplanner.domain.budget.Traveler
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class ExpenseInput(
    /** Em BRL (já convertido, se [currency] não for BRL). */
    val amountCents: Long,
    val category: ExpenseCategory,
    val date: LocalDate,
    val description: String,
    val currency: Currency = Currency.BRL,
    val originalCents: Long? = null,
    val paidBy: String? = null,
    val splitAmong: List<String> = emptyList(),
)

/** Viajantes da viagem, para dividir gastos (RF-49). */
interface TravelerRepository {
    fun observe(tripId: String): Flow<List<Traveler>>
    suspend fun add(tripId: String, name: String)
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
}

/** Resultado de uma cotação: quantos BRL vale 1 unidade, em que data, e se veio do que estava salvo. */
data class ExchangeRate(val currency: Currency, val rateToBrl: Double, val date: LocalDate, val offline: Boolean)

/** Cotações diárias para converter para BRL (RF-48), salvas no aparelho (RNF-23). */
interface CurrencyRepository {
    /**
     * Cotação de [currency] para [date] (dias futuros usam a mais recente). Sem internet, usa a última
     * salva; `null` se nunca houve cotação dessa moeda.
     */
    suspend fun rate(currency: Currency, date: LocalDate): ExchangeRate?
}

/** Orçamento e gastos avulsos de uma viagem (RF-22 a RF-24). */
interface BudgetRepository {
    fun observeExpenses(tripId: String): Flow<List<Expense>>
    fun observeCategoryBudgets(tripId: String): Flow<Map<ExpenseCategory, Long>>
    suspend fun getExpense(id: String): Expense?
    suspend fun saveExpense(tripId: String, id: String?, input: ExpenseInput): String
    suspend fun deleteExpense(id: String)

    /** Define o orçamento total e por categoria; `null` remove o valor. */
    suspend fun setBudget(tripId: String, totalCents: Long?, perCategory: Map<ExpenseCategory, Long?>)
}

data class ChecklistItem(val id: String, val tripId: String, val text: String, val done: Boolean)

/** Checklist pré-viagem (RF-26). */
interface ChecklistRepository {
    fun observe(tripId: String): Flow<List<ChecklistItem>>
    suspend fun add(tripId: String, texts: List<String>)
    suspend fun setDone(id: String, done: Boolean)
    suspend fun rename(id: String, text: String)
    suspend fun delete(id: String)
}
