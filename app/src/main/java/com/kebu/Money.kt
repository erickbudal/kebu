package com.kebu

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object Money {

    const val MAX_INPUT_DIGITS = 9

    private val LOCALE: Locale = Locale.forLanguageTag("pt-BR")

    private val CURRENCY_FORMAT: NumberFormat = NumberFormat.getCurrencyInstance(LOCALE)

    private val INPUT_FORMAT: NumberFormat = NumberFormat.getNumberInstance(LOCALE).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun format(cents: Int): String = CURRENCY_FORMAT.format(toDecimal(cents))

    fun formatInput(cents: Int): String = INPUT_FORMAT.format(toDecimal(cents))

    fun digitsToCents(digits: String): Int =
        digits.take(MAX_INPUT_DIGITS).toIntOrNull() ?: 0

    private fun toDecimal(cents: Int): BigDecimal = BigDecimal.valueOf(cents.toLong(), 2)
}