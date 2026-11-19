package com.kebu.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kebu.ExpenseApplication
import com.kebu.MAX_DESCRIPTION_LENGTH
import com.kebu.data.DatabaseBackup
import com.kebu.data.ExpenseFilter
import com.kebu.data.ExpenseRepository
import com.kebu.data.Tag
import kotlinx.coroutines.CancellationException
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
import java.time.YearMonth

data class AddExpenseUiState(
    val amountCents: Int = 0,
    val category: String? = null,
    val selectedTags: List<Tag> = emptyList(),
    val description: String = "",
    val tagQuery: String = "",
) {
    val canSave: Boolean
        get() = category != null && amountCents > 0
}

data class SavedExpense(
    val id: Long,
    val amountCents: Int,
    val category: String,
    val tagNames: List<String>,
)

data class BackupNotice(val success: Boolean, val id: Long = System.nanoTime())

class AddExpenseViewModel(
    private val repository: ExpenseRepository,
    private val databaseBackup: DatabaseBackup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _lastSaved = MutableStateFlow<SavedExpense?>(null)
    val lastSaved: StateFlow<SavedExpense?> = _lastSaved.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _backingUp = MutableStateFlow(false)
    val backingUp: StateFlow<Boolean> = _backingUp.asStateFlow()

    private val _backupNotice = MutableStateFlow<BackupNotice?>(null)
    val backupNotice: StateFlow<BackupNotice?> = _backupNotice.asStateFlow()

    private var lastSavedForm: AddExpenseUiState? = null

    val currentMonth: YearMonth = YearMonth.now()

    val monthTotalCents: StateFlow<Int> = repository
        .observeTotal(currentMonth.atDay(1), currentMonth.atEndOfMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val detailOpen = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<DetailUiState?> = detailOpen
        .flatMapLatest { isOpen ->
            if (isOpen) {
                repository
                    .observeExpenses(currentMonth.atDay(1), currentMonth.atEndOfMonth(), ExpenseFilter.All)
                    .map { expenses -> DetailUiState(ExpenseFilter.All, expenses) }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val editor = ExpenseEditor(repository, viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tagSuggestions: StateFlow<List<Tag>> = _uiState
        .map { it.tagQuery.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isEmpty()) repository.observeMostUsedTags() else repository.observeTagSearch(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onAmountChange(cents: Int) {
        _uiState.update { it.copy(amountCents = cents) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onDescriptionChange(text: String) {
        if (text.length <= MAX_DESCRIPTION_LENGTH) {
            _uiState.update { it.copy(description = text) }
        }
    }

    fun onTagQueryChange(text: String) {
        _uiState.update { it.copy(tagQuery = text) }
    }

    fun onTagAdd(tag: Tag) {
        _uiState.update { state ->
            if (state.selectedTags.any { it.id == tag.id }) {
                state
            } else {
                state.copy(selectedTags = state.selectedTags + tag, tagQuery = "")
            }
        }
    }

    fun onTagRemove(tag: Tag) {
        _uiState.update { state ->
            state.copy(selectedTags = state.selectedTags.filterNot { it.id == tag.id })
        }
    }

    fun openMonthDetail() {
        detailOpen.value = true
    }

    fun closeDetail() {
        editor.close()
        detailOpen.value = false
    }

    fun save() {
        if (_saving.value) return
        val state = _uiState.value
        val amountCents = state.amountCents.takeIf { it > 0 } ?: return
        val category = state.category ?: return

        _saving.value = true
        viewModelScope.launch {
            try {
                val expenseId = repository.addExpense(
                    amountCents = amountCents,
                    category = category,
                    description = state.description.trim().takeIf { it.isNotEmpty() },
                    tagIds = state.selectedTags.map { it.id },
                )
                if (databaseBackup.isDailyBackupDue()) {
                    runBackup { databaseBackup.createDailyBackup() }
                }
                lastSavedForm = state
                _uiState.value = AddExpenseUiState()
                _lastSaved.value = SavedExpense(
                    id = expenseId,
                    amountCents = amountCents,
                    category = category,
                    tagNames = state.selectedTags.map { it.name },
                )
            } finally {
                _saving.value = false
            }
        }
    }

    fun backupNow() {
        if (_backingUp.value) return
        viewModelScope.launch {
            runBackup { databaseBackup.createBackup() }
        }
    }

    fun undoLastSave() {
        val saved = _lastSaved.value ?: return
        _lastSaved.value = null
        _uiState.value = lastSavedForm ?: AddExpenseUiState()
        lastSavedForm = null
        viewModelScope.launch {
            repository.deleteExpense(saved.id)
        }
    }

    fun onSavedMessageDismissed() {
        _lastSaved.value = null
        lastSavedForm = null
    }

    fun onBackupNoticeDismissed() {
        _backupNotice.value = null
    }

    private suspend fun runBackup(backup: suspend () -> Unit) {
        _backingUp.value = true
        val success = try {
            backup()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backup do banco falhou", e)
            false
        } finally {
            _backingUp.value = false
        }
        _backupNotice.value = BackupNotice(success)
    }

    companion object {
        private const val TAG = "AddExpenseViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExpenseApplication
                AddExpenseViewModel(application.repository, application.databaseBackup)
            }
        }
    }
}