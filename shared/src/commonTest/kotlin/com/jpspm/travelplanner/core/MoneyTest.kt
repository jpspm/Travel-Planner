package com.jpspm.travelplanner.core

import com.jpspm.travelplanner.core.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun format() {
        assertEquals("R$ 0,00", Money.format(0))
        assertEquals("R$ 0,05", Money.format(5))
        assertEquals("R$ 12,30", Money.format(1230))
        assertEquals("R$ 1.234,56", Money.format(123456))
        assertEquals("R$ 1.000.000,00", Money.format(100_000_000))
        assertEquals("-R$ 10,00", Money.format(-1000))
    }

    @Test
    fun parseBrazilianFormats() {
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123456L, Money.parse("R$ 1.234,56"))
        assertEquals(123450L, Money.parse("1234,5"))
        assertEquals(123400L, Money.parse("1234"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(123456L, Money.parse("1234.56"))
        assertEquals(5L, Money.parse("0,05"))
    }

    @Test
    fun parseInvalid() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12,345"))
        assertNull(Money.parse("1,2,3"))
    }

    @Test
    fun inputRoundTrip() {
        listOf(0L, 5L, 1230L, 123456L).forEach { assertEquals(it, Money.parse(Money.toInput(it))) }
    }
}
