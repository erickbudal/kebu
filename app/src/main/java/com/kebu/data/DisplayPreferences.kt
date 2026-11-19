package com.kebu.data

import android.content.Context
import androidx.core.content.edit

class DisplayPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMonthTotalHidden(): Boolean = prefs.getBoolean(KEY_HIDE_MONTH_TOTAL, false)

    fun setMonthTotalHidden(hidden: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE_MONTH_TOTAL, hidden) }
    }

    private companion object {
        const val PREFS_NAME = "display"
        const val KEY_HIDE_MONTH_TOTAL = "hide_month_total"
    }
}
