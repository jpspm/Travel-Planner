package com.jpspm.travelplanner.core.money

import kotlin.math.abs

/**
 * Valores monetários em centavos de BRL (RF-25). Formatação isolada para facilitar
 * outras moedas no futuro (RNF-17).
 */
object Money {

    /** `123456` → `R$ 1.234,56` */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        val reais = (value / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val centavos = (value % 100).toString().padStart(2, '0')
        return "${sign}R$ $reais,$centavos"
    }

    /** `123456` → `1234,56` (para preencher um campo de edição) */
    fun toInput(cents: Long): String =
        "${cents / 100},${(abs(cents) % 100).toString().padStart(2, '0')}"

    /**
     * Converte o que o usuário digitou em centavos. Aceita `1234`, `1234,5`, `1.234,56`, `R$ 1.234,56`
     * e também ponto como separador decimal (`1234.56`). Retorna `null` se não for um valor válido.
     */
    fun parse(input: String): Long? {
        val cleaned = input.replace("R$", "").replace(" ", "").trim()
        if (cleaned.isEmpty()) return null
        val normalized = when {
            ',' in cleaned -> cleaned.replace(".", "").replace(',', '.')
            cleaned.count { it == '.' } == 1 && cleaned.substringAfter('.').length <= 2 -> cleaned
            else -> cleaned.replace(".", "")
        }
        if (!Regex("""-?\d+(\.\d{1,2})?""").matches(normalized)) return null
        val negative = normalized.startsWith('-')
        val digits = normalized.removePrefix("-")
        val whole = digits.substringBefore('.').toLongOrNull() ?: return null
        val fraction = digits.substringAfter('.', "").padEnd(2, '0').toLong()
        val cents = whole * 100 + fraction
        return if (negative) -cents else cents
    }
}
