package br.com.creatorassist.domain

import java.math.BigDecimal

enum class CalculationStatus {
    OK,
    NEEDS_HUMAN_REVIEW,
    INSUFFICIENT_DATA
}

data class TaxResult(
    val taxableBase: BigDecimal,
    val taxDue: BigDecimal,
    val status: CalculationStatus,
    val notes: List<String> = emptyList()
)
