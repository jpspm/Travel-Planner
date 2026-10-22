package com.jpspm.travelplanner.domain.repository

import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class ExpenseInput(
    val amountCents: Long,
    val category: ExpenseCategory,
    val date: LocalDate,
    val description: String,
)

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
