package br.com.creatorassist.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val brazilianCurrencyFormat = DecimalFormat(
    "#,##0.00",
    DecimalFormatSymbols(Locale("pt", "BR"))
)

/**
 * Single source of truth for currency formatting, used everywhere a
 * BigDecimal amount is shown to the end user (reports, warnings) —
 * so "R$ 9.000,00" (Brazilian format) never accidentally leaks out as
 * "R$ 9000.00" (Kotlin's default BigDecimal toString) from some other
 * corner of the codebase.
 */
fun BigDecimal.toBrl(): String = "R$ " + brazilianCurrencyFormat.format(this)