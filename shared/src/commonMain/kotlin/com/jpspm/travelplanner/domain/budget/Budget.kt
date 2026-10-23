package com.jpspm.travelplanner.domain.budget

import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.domain.model.Reservation
import com.jpspm.travelplanner.domain.model.ReservationType
import kotlinx.datetime.LocalDate

/** Categorias de gasto (RF-22, RF-23). O rótulo exibido fica na camada de UI. */
enum class ExpenseCategory { TRANSPORT, LODGING, FOOD, ACTIVITIES, SHOPPING, OTHER }

/** Categoria em que o valor pago de cada tipo de reserva entra no orçamento. */
val ReservationType.expenseCategory: ExpenseCategory
    get() = when (this) {
        ReservationType.FLIGHT, ReservationType.TRAIN_BUS, ReservationType.CAR_RENTAL -> ExpenseCategory.TRANSPORT
        ReservationType.LODGING -> ExpenseCategory.LODGING
        ReservationType.RESTAURANT -> ExpenseCategory.FOOD
        ReservationType.EVENT -> ExpenseCategory.ACTIVITIES
        ReservationType.OTHER -> ExpenseCategory.OTHER
    }

data class Expense(
    val id: String,
    val tripId: String,
    /** Sempre em BRL; se o gasto foi em outra moeda, já convertido (RF-48). */
    val amountCents: Long,
    val category: ExpenseCategory,
    val date: LocalDate,
    val description: String,
    val currency: Currency = Currency.BRL,
    /** Valor na moeda original, quando não foi em BRL. */
    val originalCents: Long? = null,
    /** Viajante que pagou (RF-49); `null` = o primeiro. */
    val paidBy: String? = null,
    /** Viajantes que dividem; vazio = todos. */
    val splitAmong: List<String> = emptyList(),
)

/** Um gasto da lista: avulso ou o valor pago de uma reserva (que entra automaticamente, RF-23). */
sealed interface SpendingItem {
    val amountCents: Long
    val category: ExpenseCategory
    val date: LocalDate?

    data class Manual(val expense: Expense) : SpendingItem {
        override val amountCents get() = expense.amountCents
        override val category get() = expense.category
        override val date: LocalDate? get() = expense.date
    }

    data class FromReservation(val reservation: Reservation) : SpendingItem {
        override val amountCents get() = reservation.priceCents ?: 0
        override val category get() = reservation.type.expenseCategory
        override val date: LocalDate? get() = reservation.start?.date
    }
}

data class BudgetLine(val category: ExpenseCategory, val plannedCents: Long?, val spentCents: Long) {
    /** Fração gasta do planejado (pode passar de 1); `null` sem planejado. */
    val usage: Float? get() = plannedCents?.takeIf { it > 0 }?.let { spentCents.toFloat() / it }
}

/** Planejado × gasto (RF-24). */
data class BudgetSummary(
    val plannedTotalCents: Long?,
    val spentTotalCents: Long,
    /** Uma linha por categoria que tenha planejado ou gasto, na ordem do enum. */
    val lines: List<BudgetLine>,
    /** Gastos em ordem de data, mais recentes primeiro; sem data no fim. */
    val items: List<SpendingItem>,
) {
    val remainingCents: Long? get() = plannedTotalCents?.let { it - spentTotalCents }
    val usage: Float? get() = plannedTotalCents?.takeIf { it > 0 }?.let { spentTotalCents.toFloat() / it }
}

object BudgetCalculator {

    /**
     * [plannedTotalCents] é o orçamento total; sem ele, vale a soma dos planejados por categoria,
     * se houver algum.
     */
    fun summarize(
        plannedTotalCents: Long?,
        categoryBudgets: Map<ExpenseCategory, Long>,
        expenses: List<Expense>,
        reservations: List<Reservation>,
    ): BudgetSummary {
        val items: List<SpendingItem> =
            expenses.map { SpendingItem.Manual(it) } +
                reservations.filter { (it.priceCents ?: 0) > 0 }.map { SpendingItem.FromReservation(it) }
        val spentByCategory = items.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
        val lines = ExpenseCategory.entries.mapNotNull { category ->
            val planned = categoryBudgets[category]
            val spent = spentByCategory[category] ?: 0
            if (planned == null && spent == 0L) null else BudgetLine(category, planned, spent)
        }
        val planned = plannedTotalCents ?: categoryBudgets.values.takeIf { it.isNotEmpty() }?.sum()
        return BudgetSummary(
            plannedTotalCents = planned,
            spentTotalCents = items.sumOf { it.amountCents },
            lines = lines,
            items = items.sortedWith(compareByDescending<SpendingItem, LocalDate?>(nullsFirst()) { it.date }),
        )
    }
}
