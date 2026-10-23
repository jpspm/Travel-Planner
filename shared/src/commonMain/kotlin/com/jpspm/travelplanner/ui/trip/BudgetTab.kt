package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.core.money.MoneyFormat
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.budget.BudgetCalculator
import com.jpspm.travelplanner.domain.budget.BudgetLine
import com.jpspm.travelplanner.domain.budget.BudgetSummary
import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import com.jpspm.travelplanner.domain.budget.Settlement
import com.jpspm.travelplanner.domain.budget.SpendingItem
import com.jpspm.travelplanner.domain.budget.SplitCalculator
import com.jpspm.travelplanner.domain.budget.Traveler
import com.jpspm.travelplanner.domain.repository.BudgetRepository
import com.jpspm.travelplanner.domain.repository.ExpenseInput
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TravelerRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.*
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.DateField
import com.jpspm.travelplanner.ui.components.EmptyState
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.components.MoneyInput
import com.jpspm.travelplanner.ui.components.rememberRate
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.TypeBadge
import com.jpspm.travelplanner.ui.components.emoji
import com.jpspm.travelplanner.ui.components.label
import com.jpspm.travelplanner.ui.components.today
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class BudgetUiState(
    val loading: Boolean = true,
    val summary: BudgetSummary = BudgetSummary(null, 0, emptyList(), emptyList()),
    /** Valores digitados no orçamento (o total pode ser nulo mesmo com categorias). */
    val plannedTotalCents: Long? = null,
    val categoryBudgets: Map<ExpenseCategory, Long> = emptyMap(),
    val tripStart: LocalDate? = null,
    val tripEnd: LocalDate? = null,
    val travelers: List<Traveler> = emptyList(),
    val settlement: Settlement = Settlement(emptyList(), emptyList()),
)

class BudgetViewModel(
    private val tripId: String,
    private val budget: BudgetRepository,
    trips: TripRepository,
    reservations: ReservationRepository,
    private val travelerRepo: TravelerRepository,
) : ViewModel() {

    val state: StateFlow<BudgetUiState> = combine(
        trips.observeTrip(tripId),
        budget.observeExpenses(tripId),
        budget.observeCategoryBudgets(tripId),
        reservations.observeReservations(tripId),
        travelerRepo.observe(tripId),
    ) { trip, expenses, categoryBudgets, reservationList, travelers ->
        val summary = BudgetCalculator.summarize(trip?.budgetTotalCents, categoryBudgets, expenses, reservationList)
        BudgetUiState(
            loading = false,
            summary = summary,
            plannedTotalCents = trip?.budgetTotalCents,
            categoryBudgets = categoryBudgets,
            tripStart = trip?.startDate,
            tripEnd = trip?.endDate,
            travelers = travelers,
            settlement = SplitCalculator.settle(
                travelers,
                expenses,
                summary.items.filterIsInstance<SpendingItem.FromReservation>().map { it.amountCents },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun addTraveler(name: String) = viewModelScope.launch { travelerRepo.add(tripId, name) }

    fun renameTraveler(id: String, name: String) = viewModelScope.launch { travelerRepo.rename(id, name) }

    fun deleteTraveler(id: String) = viewModelScope.launch { travelerRepo.delete(id) }

    fun saveExpense(id: String?, input: ExpenseInput) = viewModelScope.launch { budget.saveExpense(tripId, id, input) }

    fun deleteExpense(id: String) = viewModelScope.launch { budget.deleteExpense(id) }

    fun setBudget(total: Long?, perCategory: Map<ExpenseCategory, Long?>) =
        viewModelScope.launch { budget.setBudget(tripId, total, perCategory) }
}

val ExpenseCategory.label
    get() = when (this) {
        ExpenseCategory.TRANSPORT -> Res.string.category_transport
        ExpenseCategory.LODGING -> Res.string.category_lodging
        ExpenseCategory.FOOD -> Res.string.category_food
        ExpenseCategory.ACTIVITIES -> Res.string.category_activities
        ExpenseCategory.SHOPPING -> Res.string.category_shopping
        ExpenseCategory.OTHER -> Res.string.category_other
    }

val ExpenseCategory.emoji: String
    get() = when (this) {
        ExpenseCategory.TRANSPORT -> "🚌"
        ExpenseCategory.LODGING -> "🛏️"
        ExpenseCategory.FOOD -> "🍝"
        ExpenseCategory.ACTIVITIES -> "🎟️"
        ExpenseCategory.SHOPPING -> "🛍️"
        ExpenseCategory.OTHER -> "💸"
    }

/** Aba Gastos (RF-22 a RF-24): planejado × gasto, por categoria, e a lista de gastos. */
@Composable
fun BudgetTab(
    tripId: String,
    onOpenReservation: (String) -> Unit,
    addingExpense: Boolean,
    onAddingExpenseChange: (Boolean) -> Unit,
    modifier: Modifier,
    viewModel: BudgetViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingBudget by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var deletingExpense by remember { mutableStateOf<Expense?>(null) }
    var addingTraveler by remember { mutableStateOf(false) }
    var editingTraveler by remember { mutableStateOf<Traveler?>(null) }
    var deletingTraveler by remember { mutableStateOf<Traveler?>(null) }
    val summary = state.summary

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = modifier) {
        item(key = "summary") { SummaryCard(summary, onEditBudget = { editingBudget = true }) }
        if (summary.lines.isNotEmpty()) {
            item(key = "categories-header") { SectionHeader(stringResource(Res.string.budget_by_category)) }
            items(summary.lines, key = { "line:${it.category}" }) { CategoryLine(it) }
        }
        item(key = "travelers") {
            TravelersCard(
                travelers = state.travelers,
                onAdd = { addingTraveler = true },
                onEdit = { editingTraveler = it },
            )
        }
        if (state.travelers.size >= 2) {
            item(key = "settlement") {
                SettlementCard(
                    settlement = state.settlement,
                    firstTraveler = state.travelers.first(),
                    hasReservations = summary.items.any { it is SpendingItem.FromReservation },
                )
            }
        }
        item(key = "items-header") { SectionHeader(stringResource(Res.string.budget_expenses), Modifier.padding(top = 8.dp)) }
        if (summary.items.isEmpty() && !state.loading) {
            item(key = "empty") {
                EmptyState("💰", stringResource(Res.string.budget_empty_title), stringResource(Res.string.budget_empty_message))
            }
        }
        items(summary.items, key = {
            when (it) {
                is SpendingItem.Manual -> "e:${it.expense.id}"
                is SpendingItem.FromReservation -> "r:${it.reservation.id}"
            }
        }) { item ->
            SpendingRow(
                item = item,
                onClick = {
                    when (item) {
                        is SpendingItem.Manual -> editingExpense = item.expense
                        is SpendingItem.FromReservation -> onOpenReservation(item.reservation.id)
                    }
                },
            )
        }
    }

    if (editingBudget) {
        BudgetDialog(
            total = state.plannedTotalCents,
            perCategory = state.categoryBudgets,
            onSave = { total, perCategory -> viewModel.setBudget(total, perCategory); editingBudget = false },
            onDismiss = { editingBudget = false },
        )
    }
    if (addingExpense || editingExpense != null) {
        val defaultDate = today().let { t ->
            val start = state.tripStart
            val end = state.tripEnd
            if (start != null && end != null && (t < start || t > end)) start else t
        }
        ExpenseDialog(
            expense = editingExpense,
            defaultDate = defaultDate,
            travelers = state.travelers,
            onSave = { input ->
                viewModel.saveExpense(editingExpense?.id, input)
                editingExpense = null
                onAddingExpenseChange(false)
            },
            onDelete = editingExpense?.let { e -> { deletingExpense = e; editingExpense = null } },
            onDismiss = { editingExpense = null; onAddingExpenseChange(false) },
        )
    }
    deletingExpense?.let { expense ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.expense_delete_title),
            message = stringResource(Res.string.expense_delete_message, Money.format(expense.amountCents)),
            onConfirm = { viewModel.deleteExpense(expense.id) },
            onDismiss = { deletingExpense = null },
        )
    }
    if (addingTraveler || editingTraveler != null) {
        TravelerDialog(
            traveler = editingTraveler,
            onSave = { name ->
                editingTraveler?.let { viewModel.renameTraveler(it.id, name) } ?: viewModel.addTraveler(name)
                addingTraveler = false
                editingTraveler = null
            },
            onDelete = editingTraveler?.let { t -> { deletingTraveler = t; editingTraveler = null } },
            onDismiss = { addingTraveler = false; editingTraveler = null },
        )
    }
    deletingTraveler?.let { traveler ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.travelers_edit),
            message = stringResource(Res.string.travelers_delete_message, traveler.name),
            onConfirm = { viewModel.deleteTraveler(traveler.id) },
            onDismiss = { deletingTraveler = null },
        )
    }
}

@Composable
private fun TravelersCard(travelers: List<Traveler>, onAdd: () -> Unit, onEdit: (Traveler) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.travelers_title), Modifier.padding(top = 8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (travelers.size < 2) {
                Text(
                    stringResource(Res.string.travelers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                travelers.forEach { t -> AssistChip(onClick = { onEdit(t) }, label = { Text(t.name) }) }
                AssistChip(onClick = onAdd, label = { Text("+ " + stringResource(Res.string.travelers_add)) })
            }
        }
    }
}

@Composable
private fun SettlementCard(settlement: Settlement, firstTraveler: Traveler, hasReservations: Boolean) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.settle_title), style = MaterialTheme.typography.titleMedium)
            settlement.balances.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.traveler.name, modifier = Modifier.weight(1f))
                    val net = b.netCents
                    Text(
                        when {
                            net > 0 -> stringResource(Res.string.settle_balance_receive, Money.format(net))
                            net < 0 -> stringResource(Res.string.settle_balance_pay, Money.format(-net))
                            else -> stringResource(Res.string.settle_balance_even)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (settlement.transfers.isEmpty()) {
                Text(stringResource(Res.string.settle_all_even), style = MaterialTheme.typography.bodyMedium)
            }
            settlement.transfers.forEach { t ->
                Text(
                    "➡️ " + stringResource(Res.string.settle_transfer, t.from.name, Money.format(t.amountCents), t.to.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (hasReservations) {
                Text(
                    stringResource(Res.string.settle_reservations_hint, firstTraveler.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TravelerDialog(traveler: Traveler?, onSave: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(traveler?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (traveler == null) Res.string.travelers_add else Res.string.travelers_edit)) },
        text = {
            FormTextField(
                label = stringResource(Res.string.travelers_name),
                value = name,
                onValueChange = { name = it },
                capitalization = KeyboardCapitalization.Words,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name) }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun SummaryCard(summary: BudgetSummary, onEditBudget: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.budget_spent), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(summary.spentTotalCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            val planned = summary.plannedTotalCents
            if (planned != null) {
                UsageBar(summary.usage)
                val remaining = summary.remainingCents ?: 0
                Text(
                    if (remaining >= 0) stringResource(Res.string.budget_remaining, Money.format(remaining), Money.format(planned))
                    else stringResource(Res.string.budget_over, Money.format(-remaining), Money.format(planned)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    stringResource(Res.string.budget_no_plan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEditBudget, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(if (planned == null) Res.string.budget_set else Res.string.budget_edit))
            }
        }
    }
}

@Composable
private fun UsageBar(usage: Float?) {
    if (usage == null) return
    LinearProgressIndicator(
        progress = { usage.coerceIn(0f, 1f) },
        color = if (usage > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryLine(line: BudgetLine) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${line.category.emoji}  ${stringResource(line.category.label)}", modifier = Modifier.weight(1f))
            Text(
                line.plannedCents?.let { "${Money.format(line.spentCents)} / ${Money.format(it)}" } ?: Money.format(line.spentCents),
                style = MaterialTheme.typography.bodyMedium,
                color = if ((line.usage ?: 0f) > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        UsageBar(line.usage)
    }
}

@Composable
private fun SpendingRow(item: SpendingItem, onClick: () -> Unit) {
    val (title, subtitle) = when (item) {
        is SpendingItem.Manual -> item.expense.description.ifBlank { stringResource(item.category.label) } to
            listOfNotNull(
                stringResource(item.category.label),
                TimeFormat.weekdayDayMonth(item.expense.date),
                item.expense.originalCents?.takeIf { item.expense.currency != Currency.BRL }
                    ?.let { stringResource(Res.string.money_original, MoneyFormat.format(it, item.expense.currency)) },
            ).joinToString(" · ")
        is SpendingItem.FromReservation -> item.reservation.title.ifBlank { stringResource(item.reservation.type.label) } to
            listOfNotNull(
                stringResource(Res.string.budget_from_reservation),
                item.date?.let(TimeFormat::weekdayDayMonth),
                item.reservation.priceOriginalCents?.takeIf { item.reservation.priceCurrency != Currency.BRL }
                    ?.let { stringResource(Res.string.money_original, MoneyFormat.format(it, item.reservation.priceCurrency)) },
            ).joinToString(" · ")
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            TypeBadge(if (item is SpendingItem.FromReservation) item.reservation.type.emoji else item.category.emoji)
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Text(Money.format(item.amountCents), fontWeight = FontWeight.SemiBold) },
    )
}

@Composable
private fun ExpenseDialog(
    expense: Expense?,
    defaultDate: LocalDate,
    travelers: List<Traveler>,
    onSave: (ExpenseInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf((expense?.originalCents ?: expense?.amountCents)?.let(Money::toInput).orEmpty()) }
    var currency by remember { mutableStateOf(expense?.currency ?: Currency.BRL) }
    val ids = travelers.map { it.id }
    var paidBy by remember { mutableStateOf(expense?.paidBy?.takeIf { it in ids } ?: ids.firstOrNull()) }
    var splitAmong by remember { mutableStateOf(expense?.splitAmong?.filter { it in ids }?.ifEmpty { null }?.toSet() ?: ids.toSet()) }
    var category by remember { mutableStateOf(expense?.category ?: ExpenseCategory.FOOD) }
    var date by remember { mutableStateOf(expense?.date ?: defaultDate) }
    var description by remember { mutableStateOf(expense?.description.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    val cents = Money.parse(amount)
    val rate = rememberRate(currency, date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (expense == null) Res.string.expense_new else Res.string.expense_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyInput(
                    label = stringResource(Res.string.expense_amount),
                    amount = amount,
                    onAmountChange = { amount = it },
                    currency = currency,
                    onCurrencyChange = { currency = it },
                    rate = rate,
                    isError = showErrors && (cents == null || cents <= 0),
                    errorText = stringResource(Res.string.error_invalid_price),
                )
                FormTextField(
                    label = stringResource(Res.string.expense_description),
                    value = description,
                    onValueChange = { description = it },
                )
                DateField(label = stringResource(Res.string.field_date), date = date, onDateChange = { date = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { c ->
                        FilterChip(
                            selected = c == category,
                            onClick = { category = c },
                            label = { Text("${c.emoji} ${stringResource(c.label)}") },
                        )
                    }
                }
                if (travelers.size >= 2) {
                    Text(stringResource(Res.string.expense_paid_by), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        travelers.forEach { t ->
                            FilterChip(selected = t.id == paidBy, onClick = { paidBy = t.id }, label = { Text(t.name) })
                        }
                    }
                    Text(stringResource(Res.string.expense_split_among), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        travelers.forEach { t ->
                            FilterChip(
                                selected = t.id in splitAmong,
                                onClick = {
                                    // Sempre fica ao menos uma pessoa na divisão.
                                    splitAmong = if (t.id in splitAmong) (splitAmong - t.id).ifEmpty { splitAmong } else splitAmong + t.id
                                },
                                label = { Text(t.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val brl = cents?.let { rate.toBrl(it) }
                if (cents == null || cents <= 0) showErrors = true
                else if (brl != null) onSave(
                    ExpenseInput(
                        amountCents = brl,
                        category = category,
                        date = date,
                        description = description,
                        currency = currency,
                        originalCents = cents.takeIf { currency != Currency.BRL },
                        paidBy = paidBy.takeIf { travelers.size >= 2 },
                        // Todos marcados = vazio, para incluir quem entrar depois.
                        splitAmong = if (splitAmong.containsAll(ids)) emptyList() else ids.filter { it in splitAmong },
                    ),
                )
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun BudgetDialog(
    total: Long?,
    perCategory: Map<ExpenseCategory, Long>,
    onSave: (Long?, Map<ExpenseCategory, Long?>) -> Unit,
    onDismiss: () -> Unit,
) {
    var totalText by remember { mutableStateOf(total?.let(Money::toInput).orEmpty()) }
    val categoryTexts = remember {
        ExpenseCategory.entries.associateWith { mutableStateOf(perCategory[it]?.let(Money::toInput).orEmpty()) }
    }
    var showErrors by remember { mutableStateOf(false) }
    fun invalid(text: String) = text.isNotBlank() && Money.parse(text) == null
    val anyInvalid = invalid(totalText) || categoryTexts.values.any { invalid(it.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.budget_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    label = stringResource(Res.string.budget_total),
                    value = totalText,
                    onValueChange = { totalText = it },
                    keyboardType = KeyboardType.Decimal,
                    isError = showErrors && invalid(totalText),
                )
                Text(
                    stringResource(Res.string.budget_categories_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExpenseCategory.entries.forEach { c ->
                    val state = categoryTexts.getValue(c)
                    FormTextField(
                        label = "${c.emoji} ${stringResource(c.label)}",
                        value = state.value,
                        onValueChange = { state.value = it },
                        keyboardType = KeyboardType.Decimal,
                        isError = showErrors && invalid(state.value),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (anyInvalid) showErrors = true
                else onSave(Money.parse(totalText), categoryTexts.mapValues { Money.parse(it.value.value) })
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
