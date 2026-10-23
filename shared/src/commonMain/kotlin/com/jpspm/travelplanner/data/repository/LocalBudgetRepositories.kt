package com.jpspm.travelplanner.data.repository

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.data.local.AppDatabase
import com.jpspm.travelplanner.data.local.CategoryBudgetEntity
import com.jpspm.travelplanner.data.local.ChecklistItemEntity
import com.jpspm.travelplanner.data.local.ExpenseEntity
import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import com.jpspm.travelplanner.domain.repository.BudgetRepository
import com.jpspm.travelplanner.domain.repository.ChecklistItem
import com.jpspm.travelplanner.domain.repository.ChecklistRepository
import com.jpspm.travelplanner.domain.repository.ExpenseInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

private fun ExpenseEntity.toDomain() = Expense(
    id = id,
    tripId = tripId,
    amountCents = amountCents,
    category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.OTHER),
    date = LocalDate.parse(date),
    description = description,
    currency = Currency.of(currency),
    originalCents = originalCents,
    paidBy = paidBy,
    splitAmong = splitAmong.split(',').filter { it.isNotBlank() },
)

class LocalBudgetRepository(
    private val db: AppDatabase,
    private val clock: Clock,
) : BudgetRepository {

    override fun observeExpenses(tripId: String): Flow<List<Expense>> =
        db.expenseDao().observeByTrip(tripId).map { list -> list.map { it.toDomain() } }

    override fun observeCategoryBudgets(tripId: String): Flow<Map<ExpenseCategory, Long>> =
        db.categoryBudgetDao().observeByTrip(tripId).map { list ->
            list.mapNotNull { e -> runCatching { ExpenseCategory.valueOf(e.category) }.getOrNull()?.let { it to e.amountCents } }.toMap()
        }

    override suspend fun getExpense(id: String): Expense? = db.expenseDao().get(id)?.toDomain()

    override suspend fun saveExpense(tripId: String, id: String?, input: ExpenseInput): String {
        val now = clock.now().toEpochMilliseconds()
        val current = id?.let { db.expenseDao().get(it) }
        val entity = ExpenseEntity(
            id = current?.id ?: newId(),
            tripId = tripId,
            amountCents = input.amountCents,
            category = input.category.name,
            date = input.date.toString(),
            description = input.description.trim(),
            createdAt = current?.createdAt ?: now,
            updatedAt = now,
            currency = input.currency.name,
            originalCents = input.originalCents,
            paidBy = input.paidBy,
            splitAmong = input.splitAmong.joinToString(","),
        )
        db.expenseDao().upsert(entity)
        return entity.id
    }

    override suspend fun deleteExpense(id: String) = db.expenseDao().delete(id)

    override suspend fun setBudget(tripId: String, totalCents: Long?, perCategory: Map<ExpenseCategory, Long?>) {
        val now = clock.now().toEpochMilliseconds()
        db.tripDao().get(tripId)?.let { db.tripDao().upsert(it.copy(budgetTotalCents = totalCents, updatedAt = now)) }
        db.categoryBudgetDao().deleteByTrip(tripId)
        perCategory.forEach { (category, cents) ->
            if (cents != null) db.categoryBudgetDao().upsert(CategoryBudgetEntity(tripId, category.name, cents, now))
        }
    }
}

class LocalChecklistRepository(
    private val db: AppDatabase,
    private val clock: Clock,
) : ChecklistRepository {

    override fun observe(tripId: String): Flow<List<ChecklistItem>> =
        db.checklistDao().observeByTrip(tripId).map { list -> list.map { ChecklistItem(it.id, it.tripId, it.text, it.done) } }

    override suspend fun add(tripId: String, texts: List<String>) {
        val now = clock.now().toEpochMilliseconds()
        var order = db.checklistDao().maxSortOrder(tripId)
        texts.map(String::trim).filter(String::isNotEmpty).forEach { text ->
            db.checklistDao().upsert(ChecklistItemEntity(newId(), tripId, text, false, ++order, now, now))
        }
    }

    override suspend fun setDone(id: String, done: Boolean) {
        val item = db.checklistDao().get(id) ?: return
        db.checklistDao().upsert(item.copy(done = done, updatedAt = clock.now().toEpochMilliseconds()))
    }

    override suspend fun rename(id: String, text: String) {
        val item = db.checklistDao().get(id) ?: return
        if (text.isBlank()) return
        db.checklistDao().upsert(item.copy(text = text.trim(), updatedAt = clock.now().toEpochMilliseconds()))
    }

    override suspend fun delete(id: String) = db.checklistDao().delete(id)
}
