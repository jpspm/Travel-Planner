package com.jpspm.travelplanner.domain

import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import com.jpspm.travelplanner.domain.budget.SplitCalculator
import com.jpspm.travelplanner.domain.budget.Traveler
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitCalculatorTest {

    private val ana = Traveler("a", "t", "Ana")
    private val bia = Traveler("b", "t", "Bia")
    private val caio = Traveler("c", "t", "Caio")

    private fun expense(cents: Long, paidBy: String?, among: List<String> = emptyList()) =
        Expense("e$cents$paidBy", "t", cents, ExpenseCategory.FOOD, LocalDate(2027, 5, 1), "", paidBy = paidBy, splitAmong = among)

    @Test
    fun equalSplitAmongEveryone() {
        val result = SplitCalculator.settle(listOf(ana, bia, caio), listOf(expense(30_000, "a")), emptyList())
        assertEquals(listOf(20_000L, -10_000L, -10_000L), result.balances.map { it.netCents })
        assertEquals(2, result.transfers.size)
        assertTrue(result.transfers.all { it.to == ana && it.amountCents == 10_000L })
    }

    @Test
    fun splitOnlyAmongSelectedAndCentsRemainderGoesToFirst() {
        // 100 centavos entre Bia e Caio: 50 cada; 101 → Bia 51, Caio 50.
        val result = SplitCalculator.settle(listOf(ana, bia, caio), listOf(expense(101, "a", listOf("b", "c"))), emptyList())
        val share = result.balances.associate { it.traveler.id to it.shareCents }
        assertEquals(0L, share["a"])
        assertEquals(51L, share["b"])
        assertEquals(50L, share["c"])
        assertEquals(101L, result.transfers.sumOf { it.amountCents })
    }

    @Test
    fun reservationsArePaidByFirstAndSplitAmongAll() {
        val result = SplitCalculator.settle(listOf(ana, bia), listOf(expense(10_000, "b")), reservationCents = listOf(40_000))
        // Ana pagou 400, Bia 100; cada uma deve 250 → Bia paga 150 para Ana.
        val transfer = result.transfers.single()
        assertEquals(bia, transfer.from)
        assertEquals(ana, transfer.to)
        assertEquals(15_000L, transfer.amountCents)
    }

    @Test
    fun unknownPayerCountsAsFirstAndEvenBalancesNeedNoTransfer() {
        val result = SplitCalculator.settle(
            listOf(ana, bia),
            listOf(expense(5_000, "removido"), expense(5_000, "b")),
            emptyList(),
        )
        assertTrue(result.transfers.isEmpty())
        assertTrue(result.balances.all { it.netCents == 0L })
    }

    @Test
    fun singleTravelerHasNothingToSettle() {
        val result = SplitCalculator.settle(listOf(ana), listOf(expense(5_000, "a")), emptyList())
        assertTrue(result.transfers.isEmpty())
    }
}
