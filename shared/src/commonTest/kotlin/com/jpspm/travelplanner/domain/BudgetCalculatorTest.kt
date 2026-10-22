package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.budget.BudgetCalculator
import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import com.jpspm.travelplanner.domain.budget.SpendingItem
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetCalculatorTest {

    private fun expense(id: String, cents: Long, category: ExpenseCategory, day: Int) =
        Expense(id, "t", cents, category, LocalDate(2027, 5, day), id)

    private fun reservation(id: String, type: ReservationType, price: Long?, day: Int?) = Reservation(
        id, "t", type, id, null,
        day?.let { ZonedLocal(LocalDateTime(2027, 5, it, 10, 0), "Europe/Rome") }, null,
        "ABC123", price, "", emptyMap(), emptyList(),
    )

    @Test
    fun reservationsWithPriceCountAutomaticallyInTheirCategory() {
        val summary = BudgetCalculator.summarize(
            plannedTotalCents = 500_000,
            categoryBudgets = mapOf(ExpenseCategory.FOOD to 100_000),
            expenses = listOf(expense("jantar", 12_000, ExpenseCategory.FOOD, 2)),
            reservations = listOf(
                reservation("voo", ReservationType.FLIGHT, 300_000, 1),
                reservation("hotel", ReservationType.LODGING, 150_000, 1),
                reservation("museu", ReservationType.EVENT, null, 3), // sem valor: não entra
            ),
        )
        assertEquals(462_000, summary.spentTotalCents)
        assertEquals(38_000, summary.remainingCents)
        assertEquals(
            listOf(ExpenseCategory.TRANSPORT to 300_000L, ExpenseCategory.LODGING to 150_000L, ExpenseCategory.FOOD to 12_000L),
            summary.lines.map { it.category to it.spentCents },
        )
        assertEquals(100_000, summary.lines.first { it.category == ExpenseCategory.FOOD }.plannedCents)
        assertEquals(3, summary.items.size)
    }

    @Test
    fun withoutTotalThePlanIsTheSumOfCategories() {
        val summary = BudgetCalculator.summarize(
            plannedTotalCents = null,
            categoryBudgets = mapOf(ExpenseCategory.FOOD to 50_000, ExpenseCategory.TRANSPORT to 70_000),
            expenses = emptyList(),
            reservations = emptyList(),
        )
        assertEquals(120_000, summary.plannedTotalCents)
        assertEquals(0, summary.spentTotalCents)
    }

    @Test
    fun noPlanMeansNoRemaining() {
        val summary = BudgetCalculator.summarize(null, emptyMap(), listOf(expense("a", 100, ExpenseCategory.OTHER, 1)), emptyList())
        assertNull(summary.plannedTotalCents)
        assertNull(summary.remainingCents)
        assertNull(summary.usage)
    }

    @Test
    fun overBudgetHasUsageAboveOne() {
        val summary = BudgetCalculator.summarize(1_000, emptyMap(), listOf(expense("a", 1_500, ExpenseCategory.OTHER, 1)), emptyList())
        assertEquals(-500, summary.remainingCents)
        assertEquals(1.5f, summary.usage)
    }

    @Test
    fun itemsAreNewestFirstAndUndatedLast() {
        val summary = BudgetCalculator.summarize(
            null, emptyMap(),
            listOf(expense("dia1", 100, ExpenseCategory.OTHER, 1), expense("dia3", 100, ExpenseCategory.OTHER, 3)),
            listOf(reservation("sem-data", ReservationType.OTHER, 100, null)),
        )
        val ids = summary.items.map {
            when (it) {
                is SpendingItem.Manual -> it.expense.id
                is SpendingItem.FromReservation -> it.reservation.id
            }
        }
        assertEquals(listOf("dia3", "dia1", "sem-data"), ids)
    }
}
