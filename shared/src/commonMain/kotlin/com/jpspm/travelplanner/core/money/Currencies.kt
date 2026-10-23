package com.jpspm.travelplanner.core.money

import kotlin.math.roundToLong

/**
 * Moedas aceitas nos gastos e reservas (RF-48): BRL, que é a moeda dos totais, e as moedas com
 * cotação de referência do Banco Central Europeu (fonte do Frankfurter).
 */
enum class Currency(val symbol: String, val label: String) {
    BRL("R$", "Real"),
    EUR("€", "Euro"),
    USD("US$", "Dólar americano"),
    GBP("£", "Libra esterlina"),
    CHF("CHF", "Franco suíço"),
    JPY("¥", "Iene"),
    CAD("C$", "Dólar canadense"),
    AUD("A$", "Dólar australiano"),
    NZD("NZ$", "Dólar neozelandês"),
    MXN("MX$", "Peso mexicano"),
    CNY("CN¥", "Yuan"),
    HKD("HK$", "Dólar de Hong Kong"),
    SGD("S$", "Dólar de Singapura"),
    KRW("₩", "Won"),
    INR("₹", "Rupia indiana"),
    THB("฿", "Baht"),
    IDR("Rp", "Rupia indonésia"),
    MYR("RM", "Ringgit"),
    PHP("₱", "Peso filipino"),
    TRY("₺", "Lira turca"),
    ZAR("R", "Rand"),
    ILS("₪", "Shekel"),
    SEK("kr", "Coroa sueca"),
    NOK("kr", "Coroa norueguesa"),
    DKK("kr", "Coroa dinamarquesa"),
    ISK("kr", "Coroa islandesa"),
    PLN("zł", "Zloty"),
    CZK("Kč", "Coroa tcheca"),
    HUF("Ft", "Florim"),
    RON("lei", "Leu"),
    ;

    companion object {
        /** As mais usadas aparecem primeiro na escolha. */
        val common = listOf(BRL, EUR, USD, GBP)

        fun of(code: String?): Currency = entries.firstOrNull { it.name == code } ?: BRL
    }
}

/** Formatação de valores em qualquer moeda, no padrão brasileiro. */
object MoneyFormat {
    /** `4550, EUR` → `€ 45,50`; BRL usa [Money.format]. */
    fun format(cents: Long, currency: Currency): String =
        if (currency == Currency.BRL) Money.format(cents) else Money.format(cents).replaceFirst("R$", currency.symbol)

    /** Converte para BRL pela cotação (quantos BRL vale 1 unidade), arredondando para o centavo. */
    fun toBrlCents(cents: Long, rateToBrl: Double): Long = (cents * rateToBrl).roundToLong()
}
