package com.kebu.ui

import com.kebu.MAX_DESCRIPTION_LENGTH
import com.kebu.data.ExpenseRepository
import com.kebu.data.ExpenseWithTags
import com.kebu.data.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditExpenseUiState(
    val original: ExpenseWithTags,
    val amountCents: Int,
    val category: String,
    val selectedTags: List<Tag>,
    val description: String,
    val expenseDate: LocalDate,
    val tagQuery: String = "",
) {
    val id: Long
        get() = original.expense.id

    val trimmedDescription: String?
        get() = description.trim().takeIf { it.isNotEmpty() }

    val hasChanges: Boolean
        get() = amountCents != original.expense.amountCents ||
            category != original.expense.category ||
            trimmedDescription != original.expense.description ||
            expenseDate != original.expense.expenseDate ||
            selectedTags.map { it.id }.toSet() != original.tags.map { it.id }.toSet()

    val canSave: Boolean
        get() = amountCents > 0 && hasChanges
}

class ExpenseEditor(
    private val repository: ExpenseRepository,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<EditExpenseUiState?>(null)
    val state: StateFlow<EditExpenseUiState?> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tagSuggestions: StateFlow<List<Tag>> = _state
        .map { it?.tagQuery?.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            when {
                query == null -> flowOf(emptyList<Tag>())
                query.isEmpty() -> repository.observeMostUsedTags()
                else -> repository.observeTagSearch(query)
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(entry: ExpenseWithTags) {
        _state.value = EditExpenseUiState(
            original = entry,
            amountCents = entry.expense.amountCents,
            category = entry.expense.category,
            selectedTags = entry.tags.sortedBy { it.name },
            description = entry.expense.description.orEmpty(),
            expenseDate = entry.expense.expenseDate,
        )
    }

    fun close() {
        _state.value = null
    }

    fun onAmountChange(cents: Int) {
        edit { it.copy(amountCents = cents) }
    }

    fun onCategoryChange(category: String) {
        edit { it.copy(category = category) }
    }

    fun onDateChange(date: LocalDate) {
        edit { it.copy(expenseDate = date) }
    }

    fun onDescriptionChange(text: String) {
        if (text.length <= MAX_DESCRIPTION_LENGTH) {
            edit { it.copy(description = text) }
        }
    }

    fun onTagQueryChange(text: String) {
        edit { it.copy(tagQuery = text) }
    }

    fun onTagAdd(tag: Tag) {
        edit { current ->
            if (current.selectedTags.any { it.id == tag.id }) {
                current
            } else {
                current.copy(selectedTags = current.selectedTags + tag, tagQuery = "")
            }
        }
    }

    fun onTagRemove(tag: Tag) {
        edit { current ->
            current.copy(selectedTags = current.selectedTags.filterNot { it.id == tag.id })
        }
    }

    fun save() {
        val editing = _state.value ?: return
        if (_busy.value || !editing.canSave) return

        _busy.value = true
        scope.launch {
            try {
                repository.updateExpense(
                    expense = editing.original.expense.copy(
                        amountCents = editing.amountCents,
                        category = editing.category,
                        description = editing.trimmedDescription,
                        expenseDate = editing.expenseDate,
                    ),
                    tagIds = editing.selectedTags.map { it.id },
                )
                closeIfEditing(editing.id)
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete() {
        val editing = _state.value ?: return
        if (_busy.value) return

        _busy.value = true
        scope.launch {
            try {
                repository.deleteExpense(editing.id)
                closeIfEditing(editing.id)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun edit(transform: (EditExpenseUiState) -> EditExpenseUiState) {
        _state.update { current -> current?.let(transform) }
    }

    private fun closeIfEditing(id: Long) {
        _state.update { current -> if (current?.id == id) null else current }
    }
}
