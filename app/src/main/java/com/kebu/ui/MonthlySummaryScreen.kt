package com.kebu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kebu.Money
import com.kebu.data.ExpenseFilter
import com.kebu.data.ExpenseWithTags
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthlySummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: MonthlySummaryViewModel = viewModel(factory = MonthlySummaryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val detail by viewModel.detail.collectAsState()

    val openDetail = detail
    if (openDetail == null) {
        SummaryContent(
            uiState = uiState,
            onPreviousMonth = viewModel::selectPreviousMonth,
            onNextMonth = viewModel::selectNextMonth,
            onOpenDetail = viewModel::openDetail,
            modifier = modifier,
        )
    } else {
        DetailContent(
            detail = openDetail,
            month = uiState.month,
            editor = viewModel.editor,
            onBack = viewModel::closeDetail,
            modifier = modifier,
        )
    }
}

@Composable
private fun SummaryContent(
    uiState: MonthlySummaryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenDetail: (ExpenseFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = formatMonth(uiState.month),
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.canSelectNextMonth) {
                IconButton(onClick = onNextMonth) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = uiState.totalCents > 0) {
                    onOpenDetail(ExpenseFilter.All)
                }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionLabel("Total")
            Text(
                text = Money.format(uiState.totalCents),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Por categoria")
            if (uiState.byCategory.isEmpty()) {
                EmptyLine("Nenhum gasto neste mês.")
            } else {
                uiState.byCategory.forEach { item ->
                    SummaryRow(
                        label = item.category,
                        amountCents = item.totalCents,
                        onClick = { onOpenDetail(ExpenseFilter.Category(item.category)) },
                    )
                }
            }
        }

        if (uiState.byTag.isNotEmpty()) {
            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Por tag")
                uiState.byTag.forEach { item ->
                    SummaryRow(
                        label = item.tagName,
                        amountCents = item.totalCents,
                        onClick = { onOpenDetail(ExpenseFilter.Tag(item.tagId, item.tagName)) },
                    )
                }
            }
        }
    }
}

@Composable
fun DetailContent(
    detail: DetailUiState,
    month: YearMonth,
    editor: ExpenseEditor,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing by editor.state.collectAsState()
    val listState = rememberLazyListState()

    val openEditing = editing
    if (openEditing != null) {
        EditExpenseContent(editor = editor, state = openEditing, modifier = modifier)
        return
    }

    BackHandler(onBack = onBack)

    val byDate = remember(detail.expenses) {
        detail.expenses.groupBy { it.expense.expenseDate }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.filter.label(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatMonth(month),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = Money.format(detail.totalCents),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        HorizontalDivider()

        if (detail.expenses.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                EmptyLine("Nenhum gasto aqui.")
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val firstDate = byDate.keys.firstOrNull()
                byDate.forEach { (date, expenses) ->
                    item(key = "date-$date") {
                        DateHeader(date, showDivider = date != firstDate)
                    }
                    items(expenses, key = { it.expense.id }) { entry ->
                        ExpenseRow(entry, onClick = { editor.open(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, showDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            text = formatDate(date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ExpenseRow(entry: ExpenseWithTags, onClick: () -> Unit) {
    val expense = entry.expense
    val description = expense.description?.takeIf { it.isNotBlank() }
    val details = buildList {
        if (description != null) add(expense.category)
        entry.tags.sortedBy { it.name }.forEach { add(it.name) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = description ?: expense.category,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = Money.format(expense.amountCents),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SummaryRow(label: String, amountCents: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = Money.format(amountCents), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

private fun ExpenseFilter.label(): String = when (this) {
    ExpenseFilter.All -> "Todos os gastos"
    is ExpenseFilter.Category -> name
    is ExpenseFilter.Tag -> name
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private val MONTH_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d", PT_BR)

private fun formatMonth(month: YearMonth): String =
    month.format(MONTH_FORMATTER).replaceFirstChar { it.uppercase() }

private fun formatDate(date: LocalDate): String =
    date.format(DATE_FORMATTER).replaceFirstChar { it.uppercase() }