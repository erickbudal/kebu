package com.kebu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Insert
    suspend fun insertExpenseTags(refs: List<ExpenseTagCrossRef>)

    @Transaction
    suspend fun insertExpenseWithTags(expense: Expense, tagIds: List<Long>): Long {
        val expenseId = insertExpense(expense)
        if (tagIds.isNotEmpty()) {
            insertExpenseTags(tagIds.map { ExpenseTagCrossRef(expenseId, it) })
        }
        return expenseId
    }

    @Update
    suspend fun updateExpense(expense: Expense): Int

    @Query("DELETE FROM expense_tag WHERE expense_id = :expenseId")
    suspend fun deleteExpenseTags(expenseId: Long)

    @Transaction
    suspend fun updateExpenseWithTags(expense: Expense, tagIds: List<Long>) {
        if (updateExpense(expense) == 0) return
        deleteExpenseTags(expense.id)
        if (tagIds.isNotEmpty()) {
            insertExpenseTags(tagIds.map { ExpenseTagCrossRef(expense.id, it) })
        }
    }

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query(
        """
        SELECT COALESCE(SUM(amount_cents), 0)
        FROM expense
        WHERE expense_date BETWEEN :start AND :end
        """
    )
    fun observeTotal(start: LocalDate, end: LocalDate): Flow<Int>

    @Query(
        """
        SELECT category, SUM(amount_cents) AS total_cents
        FROM expense
        WHERE expense_date BETWEEN :start AND :end
        GROUP BY category
        ORDER BY total_cents DESC
        """
    )
    fun observeTotalsByCategory(start: LocalDate, end: LocalDate): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT tag.id AS tag_id, tag.name AS tag_name, SUM(expense.amount_cents) AS total_cents
        FROM expense_tag
        INNER JOIN expense ON expense.id = expense_tag.expense_id
        INNER JOIN tag ON tag.id = expense_tag.tag_id
        WHERE expense.expense_date BETWEEN :start AND :end
        GROUP BY tag.id
        ORDER BY total_cents DESC
        """
    )
    fun observeTotalsByTag(start: LocalDate, end: LocalDate): Flow<List<TagTotal>>

    @Transaction
    @Query(
        """
        SELECT * FROM expense
        WHERE expense_date BETWEEN :start AND :end
        ORDER BY expense_date DESC, created_at DESC
        """
    )
    fun observeExpenses(start: LocalDate, end: LocalDate): Flow<List<ExpenseWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM expense
        WHERE expense_date BETWEEN :start AND :end
          AND category = :category
        ORDER BY expense_date DESC, created_at DESC
        """
    )
    fun observeExpensesByCategory(
        start: LocalDate,
        end: LocalDate,
        category: String,
    ): Flow<List<ExpenseWithTags>>

    @Transaction
    @Query(
        """
        SELECT expense.* FROM expense
        INNER JOIN expense_tag ON expense_tag.expense_id = expense.id
        WHERE expense.expense_date BETWEEN :start AND :end
          AND expense_tag.tag_id = :tagId
        ORDER BY expense.expense_date DESC, expense.created_at DESC
        """
    )
    fun observeExpensesByTag(
        start: LocalDate,
        end: LocalDate,
        tagId: Long,
    ): Flow<List<ExpenseWithTags>>
}

@Dao
interface TagDao {

    @Query(
        """
        SELECT tag.* FROM tag
        LEFT JOIN expense_tag ON expense_tag.tag_id = tag.id
        GROUP BY tag.id
        ORDER BY COUNT(expense_tag.expense_id) DESC, tag.name ASC
        LIMIT :limit
        """
    )
    fun observeMostUsed(limit: Int): Flow<List<Tag>>

    @Query(
        """
        SELECT * FROM tag
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 20
        """
    )
    fun observeSearch(query: String): Flow<List<Tag>>
}