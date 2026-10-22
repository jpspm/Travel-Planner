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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.budget.BudgetCalculator
import com.jpspm.travelplanner.domain.budget.BudgetLine
import com.jpspm.travelplanner.domain.budget.BudgetSummary
import com.jpspm.travelplanner.domain.budget.Expense
import com.jpspm.travelplanner.domain.budget.ExpenseCategory
import com.jpspm.travelplanner.domain.budget.SpendingItem
import com.jpspm.travelplanner.domain.repository.BudgetRepository
import com.jpspm.travelplanner.domain.repository.ExpenseInput
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.*
import com.jpspm.travelplanner.ui.components.ConfirmDeleteDialog
import com.jpspm.travelplanner.ui.components.DateField
import com.jpspm.travelplanner.ui.components.EmptyState
import com.jpspm.travelplanner.ui.components.FormTextField
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
)

class BudgetViewModel(
    private val tripId: String,
    private val budget: BudgetRepository,
    trips: TripRepository,
    reservations: ReservationRepository,
) : ViewModel() {

    val state: StateFlow<BudgetUiState> = combine(
        trips.observeTrip(tripId),
        budget.observeExpenses(tripId),
        budget.observeCategoryBudgets(tripId),
        reservations.observeReservations(tripId),
    ) { trip, expenses, categoryBudgets, reservationList ->
        BudgetUiState(
            loading = false,
            summary = BudgetCalculator.summarize(trip?.budgetTotalCents, categoryBudgets, expenses, reservationList),
            plannedTotalCents = trip?.budgetTotalCents,
            categoryBudgets = categoryBudgets,
            tripStart = trip?.startDate,
            tripEnd = trip?.endDate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

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
    val summary = state.summary

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = modifier) {
        item(key = "summary") { SummaryCard(summary, onEditBudget = { editingBudget = true }) }
        if (summary.lines.isNotEmpty()) {
            item(key = "categories-header") { SectionHeader(stringResource(Res.string.budget_by_category)) }
            items(summary.lines, key = { "line:${it.category}" }) { CategoryLine(it) }
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
            "${stringResource(item.category.label)} · ${TimeFormat.weekdayDayMonth(item.expense.date)}"
        is SpendingItem.FromReservation -> item.reservation.title.ifBlank { stringResource(item.reservation.type.label) } to
            listOfNotNull(
                stringResource(Res.string.budget_from_reservation),
                item.date?.let(TimeFormat::weekdayDayMonth),
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
    onSave: (ExpenseInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(expense?.amountCents?.let(Money::toInput).orEmpty()) }
    var category by remember { mutableStateOf(expense?.category ?: ExpenseCategory.FOOD) }
    var date by remember { mutableStateOf(expense?.date ?: defaultDate) }
    var description by remember { mutableStateOf(expense?.description.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    val cents = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (expense == null) Res.string.expense_new else Res.string.expense_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    label = stringResource(Res.string.expense_amount),
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardType = KeyboardType.Decimal,
                    isError = showErrors && (cents == null || cents <= 0),
                    supportingText = if (showErrors && (cents == null || cents <= 0)) stringResource(Res.string.error_invalid_price) else null,
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (cents == null || cents <= 0) showErrors = true
                else onSave(ExpenseInput(cents, category, date, description))
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
