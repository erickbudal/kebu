package com.kebu.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "expense")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "amount_cents")
    val amountCents: Int,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "expense_date")
    val expenseDate: LocalDate,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,
)

@Entity(
    tableName = "tag",
    indices = [Index(value = ["name"], unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,
)

@Entity(
    tableName = "expense_tag",
    primaryKeys = ["expense_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class ExpenseTagCrossRef(
    @ColumnInfo(name = "expense_id")
    val expenseId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long,
)

data class CategoryTotal(
    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "total_cents")
    val totalCents: Int,
)

data class TagTotal(
    @ColumnInfo(name = "tag_id")
    val tagId: Long,

    @ColumnInfo(name = "tag_name")
    val tagName: String,

    @ColumnInfo(name = "total_cents")
    val totalCents: Int,
)

data class ExpenseWithTags(
    @Embedded
    val expense: Expense,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ExpenseTagCrossRef::class,
            parentColumn = "expense_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<Tag>,
)