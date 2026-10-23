package com.jpspm.travelplanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxWidth
import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.core.money.MoneyFormat
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.repository.CurrencyRepository
import com.jpspm.travelplanner.domain.repository.ExchangeRate
import com.jpspm.travelplanner.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Cotação para converter um valor para BRL (RF-48). */
sealed interface RateState {
    data object Loading : RateState
    data class Ready(val rate: ExchangeRate) : RateState
    data object Missing : RateState

    /** Valor em BRL; `null` enquanto não houver cotação. */
    fun toBrl(cents: Long): Long? = when (this) {
        is Ready -> if (rate.currency == Currency.BRL) cents else MoneyFormat.toBrlCents(cents, rate.rateToBrl)
        else -> null
    }
}

/** Cotação de [currency] no dia [date]; BRL fica pronta na hora. */
@Composable
fun rememberRate(currency: Currency, date: LocalDate?, repository: CurrencyRepository = koinInject()): RateState {
    val state by produceState<RateState>(RateState.Loading, currency, date) {
        value = RateState.Loading
        val day = date ?: today()
        value = repository.rate(currency, day)?.let { RateState.Ready(it) } ?: RateState.Missing
    }
    return if (currency == Currency.BRL) RateState.Ready(ExchangeRate(Currency.BRL, 1.0, date ?: today(), offline = false)) else state
}

/**
 * Valor numa moeda qualquer, com a escolha da moeda no fim do campo e, embaixo, quanto dá em reais.
 * Totais e orçamento continuam em BRL.
 */
@Composable
fun MoneyInput(
    label: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    currency: Currency,
    onCurrencyChange: (Currency) -> Unit,
    rate: RateState,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cents = Money.parse(amount)
    val support: String? = when {
        isError && errorText != null -> errorText
        currency == Currency.BRL -> null
        rate is RateState.Loading -> stringResource(Res.string.money_rate_loading)
        rate is RateState.Missing -> stringResource(Res.string.money_rate_missing, currency.name)
        rate is RateState.Ready && cents != null && cents > 0 -> stringResource(
            if (rate.rate.offline) Res.string.money_rate_offline else Res.string.money_rate_preview,
            Money.format(rate.toBrl(cents) ?: 0),
            TimeFormat.date(rate.rate.date),
        )
        else -> null
    }
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError || (currency != Currency.BRL && rate is RateState.Missing),
        supportingText = support?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("${currency.symbol} ${currency.name} ▾") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val rest = Currency.entries - Currency.common.toSet()
                    (Currency.common + rest).forEachIndexed { i, c ->
                        if (i == Currency.common.size) HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("${c.name} · ${c.label}") },
                            onClick = { menuOpen = false; onCurrencyChange(c) },
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** `R$ 283,00` ou, se pago em outra moeda, `R$ 283,00 (€ 45,00)`. */
fun formatPaid(brlCents: Long, currency: Currency, originalCents: Long?): String =
    if (currency == Currency.BRL || originalCents == null) Money.format(brlCents)
    else "${Money.format(brlCents)} (${MoneyFormat.format(originalCents, currency)})"
