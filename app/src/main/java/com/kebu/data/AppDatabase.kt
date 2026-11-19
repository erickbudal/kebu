package com.kebu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Expense::class, Tag::class, ExpenseTagCrossRef::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    abstract fun tagDao(): TagDao

    companion object {

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        DEFAULT_TAGS.forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO tag (name) VALUES (?)", arrayOf<Any>(name))
                        }
                    }
                })
                .build()
    }
}

private val DEFAULT_TAGS = listOf(
    "verdureira",
    "garten",
    "mueller",
    "bistek",
    "concurso",
    "beach tennis",
    "her"
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `expense` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `amount_cents` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `description` TEXT,
                `expense_date` TEXT NOT NULL,
                `created_at` TEXT NOT NULL,
                `updated_at` TEXT NOT NULL
            )
            """
        )
        db.execSQL(
            """
            INSERT INTO `expense` (
                `id`, `amount_cents`, `category`, `description`,
                `expense_date`, `created_at`, `updated_at`
            )
            SELECT
                `id`, `amount_cents`, `category`, `description`,
                `expense_date`, `created_at`, `updated_at`
            FROM `expenses`
            """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tag` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL
            )
            """
        )
        db.execSQL("INSERT INTO `tag` (`id`, `name`) SELECT `id`, `name` FROM `tags`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `expense_tag` (
                `expense_id` INTEGER NOT NULL,
                `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`expense_id`, `tag_id`),
                FOREIGN KEY(`expense_id`) REFERENCES `expense`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tag_id`) REFERENCES `tag`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )
        db.execSQL(
            """
            INSERT INTO `expense_tag` (`expense_id`, `tag_id`)
            SELECT `expense_id`, `tag_id` FROM `expense_tags`
            """
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expense_tag_tag_id` ON `expense_tag` (`tag_id`)"
        )

        db.execSQL("DROP TABLE `expense_tags`")
        db.execSQL("DROP TABLE `expenses`")
        db.execSQL("DROP TABLE `tags`")
    }
}