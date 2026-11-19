package com.kebu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kebu.CATEGORIES
import com.kebu.MAX_DESCRIPTION_LENGTH
import com.kebu.Money
import com.kebu.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SAVED_MESSAGE_FADE_IN_MILLIS = 150
private const val SAVED_MESSAGE_VISIBLE_MILLIS = 5_000L
private const val SAVED_MESSAGE_FADE_OUT_MILLIS = 1_000
private const val SAVED_MESSAGE_UNDO_FADE_OUT_MILLIS = 150
private const val BACKUP_NOTICE_VISIBLE_MILLIS = 2_500L
private const val BACKUP_NOTICE_FADE_MILLIS = 300

private const val TRIPLE_TAP_TIMEOUT_MILLIS = 400L

private const val HIDDEN_AMOUNT = "R$ ••••"

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AddExpenseScreen(
    modifier: Modifier = Modifier,
    viewModel: AddExpenseViewModel = viewModel(factory = AddExpenseViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val monthTotalCents by viewModel.monthTotalCents.collectAsState()
    val monthTotalHidden by viewModel.monthTotalHidden.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()
    val lastSaved by viewModel.lastSaved.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val backingUp by viewModel.backingUp.collectAsState()
    val backupNotice by viewModel.backupNotice.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var savedMessage by remember { mutableStateOf<SavedExpense?>(null) }
    var undoDismiss by remember { mutableStateOf(false) }
    var shownBackupNotice by remember { mutableStateOf<BackupNotice?>(null) }

    var amountField by remember {
        val initial = Money.formatInput(0)
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }

    LaunchedEffect(uiState.amountCents) {
        val formatted = Money.formatInput(uiState.amountCents)
        if (amountField.text != formatted) {
            amountField = TextFieldValue(formatted, TextRange(formatted.length))
        }
    }

    LaunchedEffect(lastSaved) {
        val saved = lastSaved ?: return@LaunchedEffect
        undoDismiss = false
        savedMessage = saved
        delay(SAVED_MESSAGE_VISIBLE_MILLIS)
        viewModel.onSavedMessageDismissed()
    }

    LaunchedEffect(backupNotice) {
        val notice = backupNotice ?: return@LaunchedEffect
        shownBackupNotice = notice
        delay(BACKUP_NOTICE_VISIBLE_MILLIS)
        viewModel.onBackupNoticeDismissed()
    }

    val openDetail = detail
    if (openDetail != null) {
        DetailContent(
            detail = openDetail,
            month = viewModel.currentMonth,
            editor = viewModel.editor,
            onBack = viewModel::closeDetail,
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(Unit) {
        amountFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(modifier = modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = monthTotalCents > 0) {
                        focusManager.clearFocus()
                        viewModel.openMonthDetail()
                    }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Gasto neste mês", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (monthTotalHidden) HIDDEN_AMOUNT else Money.format(monthTotalCents),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::toggleMonthTotalHidden) {
                        Icon(
                            painter = painterResource(if (monthTotalHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = if (monthTotalHidden) "Mostrar total" else "Ocultar total",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = amountField,
                onValueChange = { input ->
                    val cents = Money.digitsToCents(input.text.filter(Char::isDigit))
                    viewModel.onAmountChange(cents)
                    val formatted = Money.formatInput(cents)
                    amountField = TextFieldValue(formatted, TextRange(formatted.length))
                },
                label = { Text("Valor") },
                prefix = { Text("R$ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocusRequester),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Categoria")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = uiState.category == category,
                            onClick = {
                                viewModel.onCategoryChange(category)
                                if (uiState.amountCents > 0) {
                                    focusManager.clearFocus()
                                    scope.launch { scrollState.animateScrollToBottom() }
                                }
                            },
                            label = { Text(category) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Tags")

                if (uiState.selectedTags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.selectedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.onTagRemove(tag) },
                                label = { Text(tag.name) },
                                trailingIcon = { Text("✕") },
                            )
                        }
                    }
                }

                val available = tagSuggestions.filterNot { suggestion -> uiState.selectedTags.any { it.id == suggestion.id } }

                if (available.isEmpty()) {
                    Text("Nenhuma tag encontrada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        available.forEach { tag ->
                            AssistChip(
                                onClick = {
                                    val ready = uiState.canSave
                                    viewModel.onTagAdd(tag)
                                    if (ready) {
                                        focusManager.clearFocus()
                                        scope.launch { scrollState.animateScrollToBottom() }
                                    }
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.tagQuery,
                    onValueChange = viewModel::onTagQueryChange,
                    label = { Text("Buscar tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descrição") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                supportingText = { Text("${uiState.description.length}/$MAX_DESCRIPTION_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
            )

            val saveEnabled = uiState.canSave && !saving && !backingUp
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.save()
                    scope.launch { scrollState.animateScrollTo(0) }
                },
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth().pointerInput(viewModel, saveEnabled) {
                    if (!saveEnabled) detectTripleTap(viewModel::backupNow)
                },
            ) {
                Text(if (backingUp) "Fazendo backup…" else "Salvar gasto")
            }
        }

        AnimatedVisibility(
            visible = lastSaved != null,
            enter = fadeIn(tween(SAVED_MESSAGE_FADE_IN_MILLIS)),
            exit = fadeOut(tween(if (undoDismiss) SAVED_MESSAGE_UNDO_FADE_OUT_MILLIS else SAVED_MESSAGE_FADE_OUT_MILLIS)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        ) {
            savedMessage?.let { saved ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${Money.format(saved.amountCents)} · ${saved.category}", style = MaterialTheme.typography.bodyMedium)
                            if (saved.tagNames.isNotEmpty()) {
                                Text(saved.tagNames.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(
                            onClick = {
                                undoDismiss = true
                                viewModel.undoLastSave()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                        ) {
                            Text("Desfazer")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = backupNotice != null,
            enter = fadeIn(tween(BACKUP_NOTICE_FADE_MILLIS)),
            exit = fadeOut(tween(BACKUP_NOTICE_FADE_MILLIS)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        ) {
            shownBackupNotice?.let { BackupNoticeBadge(it) }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BackupNoticeBadge(notice: BackupNotice) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = if (notice.success) "✓ Backup salvo" else "Falha no backup",
        style = MaterialTheme.typography.labelLarge,
        color = if (notice.success) colors.onPrimaryContainer else colors.onErrorContainer,
        modifier = Modifier
            .background(if (notice.success) colors.primaryContainer else colors.errorContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private suspend fun PointerInputScope.detectTripleTap(onTripleTap: () -> Unit) {
    var taps = 0
    var lastUpMillis = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        if (up == null) {
            taps = 0
        } else {
            taps = if (down.uptimeMillis - lastUpMillis <= TRIPLE_TAP_TIMEOUT_MILLIS) taps + 1 else 1
            lastUpMillis = up.uptimeMillis
            if (taps == 3) {
                taps = 0
                onTripleTap()
            }
        }
    }
}

private suspend fun ScrollState.animateScrollToBottom() {
    withFrameNanos { }
    animateScrollTo(maxValue)
}