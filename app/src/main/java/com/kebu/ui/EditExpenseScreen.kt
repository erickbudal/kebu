package com.kebu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kebu.CATEGORIES
import com.kebu.MAX_DESCRIPTION_LENGTH
import com.kebu.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExpenseContent(
    editor: ExpenseEditor,
    state: EditExpenseUiState,
    modifier: Modifier = Modifier,
) {
    val tagSuggestions by editor.tagSuggestions.collectAsState()
    val busy by editor.busy.collectAsState()
    val focusManager = LocalFocusManager.current

    var amountField by remember(state.id) {
        val initial = Money.formatInput(state.amountCents)
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = editor::close)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = editor::close) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            val original = state.original.expense
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Editar gasto",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Registrado em ${formatDateTime(original.createdAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (original.updatedAt != original.createdAt) {
                    Text(
                        text = "Editado em ${formatDateTime(original.updatedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    confirmDelete = true
                },
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Remover")
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = amountField,
                onValueChange = { input ->
                    val cents = Money.digitsToCents(input.text.filter(Char::isDigit))
                    editor.onAmountChange(cents)
                    val formatted = Money.formatInput(cents)
                    amountField = TextFieldValue(formatted, TextRange(formatted.length))
                },
                label = { Text("Valor") },
                prefix = { Text("R$ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                date = state.expenseDate,
                onClick = {
                    focusManager.clearFocus()
                    showDatePicker = true
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Categoria")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (CATEGORIES + state.original.expense.category).distinct().forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { editor.onCategoryChange(category) },
                            label = { Text(category) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Tags")

                if (state.selectedTags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.selectedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { editor.onTagRemove(tag) },
                                label = { Text(tag.name) },
                                trailingIcon = { Text("✕") },
                            )
                        }
                    }
                }

                val available = tagSuggestions.filterNot { suggestion -> state.selectedTags.any { it.id == suggestion.id } }

                if (available.isEmpty()) {
                    Text("Nenhuma tag encontrada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        available.forEach { tag ->
                            AssistChip(
                                onClick = { editor.onTagAdd(tag) },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.tagQuery,
                    onValueChange = editor::onTagQueryChange,
                    label = { Text("Buscar tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = editor::onDescriptionChange,
                label = { Text("Descrição") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                supportingText = { Text("${state.description.length}/$MAX_DESCRIPTION_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    editor.save()
                },
                enabled = state.canSave && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar alterações")
            }
        }
    }

    if (showDatePicker) {
        ExpenseDatePickerDialog(
            date = state.expenseDate,
            onConfirm = { date ->
                showDatePicker = false
                editor.onDateChange(date)
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (confirmDelete) {
        val expense = state.original.expense
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remover gasto?") },
            text = {
                Text("O gasto de ${Money.format(expense.amountCents)} em ${expense.category} será apagado. Isso não pode ser desfeito.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        editor.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remover")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun DateField(date: LocalDate, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    OutlinedTextField(
        value = formatFullDate(date),
        onValueChange = {},
        readOnly = true,
        label = { Text("Data") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                        currentOnClick()
                    }
                }
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDatePickerDialog(
    date: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
        yearRange = minOf(date.year, DatePickerDefaults.YearRange.first)..maxOf(date.year, today.year),
        selectableDates = remember(today) { SelectableDatesUntil(today) },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onConfirm(it.toUtcLocalDate()) } },
                enabled = pickerState.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class SelectableDatesUntil(private val lastDate: LocalDate) : SelectableDates {

    private val lastDateMillis = lastDate.toUtcMillis()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastDateMillis

    override fun isSelectableYear(year: Int): Boolean = year <= lastDate.year
}

private val FULL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

private fun formatFullDate(date: LocalDate): String =
    date.format(FULL_DATE_FORMATTER).replaceFirstChar { it.uppercase() }

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))

private fun formatDateTime(dateTime: LocalDateTime): String = dateTime.format(DATE_TIME_FORMATTER)

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
