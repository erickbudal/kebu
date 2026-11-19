package com.kebu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kebu.ExpenseApplication
import com.kebu.data.CategoryTotal
import com.kebu.data.ExpenseFilter
import com.kebu.data.ExpenseRepository
import com.kebu.data.ExpenseWithTags
import com.kebu.data.TagTotal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

data class MonthlySummaryUiState(
    val month: YearMonth = YearMonth.now(),
    val totalCents: Int = 0,
    val byCategory: List<CategoryTotal> = emptyList(),
    val byTag: List<TagTotal> = emptyList(),
) {
    val canSelectNextMonth: Boolean
        get() = month < YearMonth.now()
}

data class DetailUiState(
    val filter: ExpenseFilter,
    val expenses: List<ExpenseWithTags>,
) {
    val totalCents: Int = expenses.sumOf { it.expense.amountCents }
}

class MonthlySummaryViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())

    private val selectedFilter = MutableStateFlow<ExpenseFilter?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlySummaryUiState> = selectedMonth
        .flatMapLatest { month ->
            val start = month.atDay(1)
            val end = month.atEndOfMonth()
            combine(
                repository.observeTotal(start, end),
                repository.observeTotalsByCategory(start, end),
                repository.observeTotalsByTag(start, end),
            ) { total, byCategory, byTag ->
                MonthlySummaryUiState(month, total, byCategory, byTag)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlySummaryUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<DetailUiState?> = combine(selectedMonth, selectedFilter, ::Pair)
        .flatMapLatest { (month, filter) ->
            if (filter == null) {
                flowOf(null)
            } else {
                repository
                    .observeExpenses(month.atDay(1), month.atEndOfMonth(), filter)
                    .map { expenses -> DetailUiState(filter, expenses) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val editor = ExpenseEditor(repository, viewModelScope)

    fun selectPreviousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun selectNextMonth() {
        selectedMonth.update { month ->
            if (month < YearMonth.now()) month.plusMonths(1) else month
        }
    }

    fun openDetail(filter: ExpenseFilter) {
        selectedFilter.value = filter
    }

    fun closeDetail() {
        editor.close()
        selectedFilter.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as ExpenseApplication
                MonthlySummaryViewModel(application.repository)
            }
        }
    }
}