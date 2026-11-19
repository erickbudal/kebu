package com.kebu

import android.app.Application
import com.kebu.data.AppDatabase
import com.kebu.data.DatabaseBackup
import com.kebu.data.DisplayPreferences
import com.kebu.data.ExpenseRepository

class ExpenseApplication : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }

    val repository by lazy { ExpenseRepository(database.expenseDao(), database.tagDao()) }

    val databaseBackup by lazy { DatabaseBackup(this, database) }

    val displayPreferences by lazy { DisplayPreferences(this) }
}
