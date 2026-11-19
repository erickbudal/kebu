package com.kebu.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface ExpenseFilter {

    data object All : ExpenseFilter

    data class Category(val name: String) : ExpenseFilter

    data class Tag(val id: Long, val name: String) : ExpenseFilter
}

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val tagDao: TagDao,
) {

    fun observeTotal(start: LocalDate, end: LocalDate): Flow<Int> =
        expenseDao.observeTotal(start, end)

    fun observeTotalsByCategory(start: LocalDate, end: LocalDate): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategory(start, end)

    fun observeTotalsByTag(start: LocalDate, end: LocalDate): Flow<List<TagTotal>> =
        expenseDao.observeTotalsByTag(start, end)

    fun observeExpenses(
        start: LocalDate,
        end: LocalDate,
        filter: ExpenseFilter,
    ): Flow<List<ExpenseWithTags>> = when (filter) {
        ExpenseFilter.All -> expenseDao.observeExpenses(start, end)
        is ExpenseFilter.Category -> expenseDao.observeExpensesByCategory(start, end, filter.name)
        is ExpenseFilter.Tag -> expenseDao.observeExpensesByTag(start, end, filter.id)
    }

    fun observeMostUsedTags(limit: Int = SUGGESTED_TAG_LIMIT): Flow<List<Tag>> =
        tagDao.observeMostUsed(limit)

    fun observeTagSearch(query: String): Flow<List<Tag>> =
        tagDao.observeSearch(query.trim())

    suspend fun addExpense(
        amountCents: Int,
        category: String,
        description: String?,
        tagIds: List<Long>,
    ): Long {
        val now = LocalDateTime.now()
        val expense = Expense(
            amountCents = amountCents,
            category = category,
            description = description,
            expenseDate = now.toLocalDate(),
            createdAt = now,
            updatedAt = now,
        )
        return expenseDao.insertExpenseWithTags(expense, tagIds)
    }

    suspend fun updateExpense(expense: Expense, tagIds: List<Long>) {
        expenseDao.updateExpenseWithTags(expense.copy(updatedAt = LocalDateTime.now()), tagIds.distinct())
    }

    suspend fun deleteExpense(id: Long) {
        expenseDao.deleteExpense(id)
    }

    companion object {
        const val SUGGESTED_TAG_LIMIT = 5
    }
}