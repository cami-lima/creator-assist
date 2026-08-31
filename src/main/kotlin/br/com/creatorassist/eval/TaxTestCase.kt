package br.com.creatorassist.eval

import br.com.creatorassist.domain.IncomeEntry
import java.math.BigDecimal

data class PreviousStateSimulated(
    val taxRegime: String,
    val yearToDateRevenue: BigDecimal
)

data class TaxTestCase(
    val id: String,
    val description: String,
    val incomeEntries: List<IncomeEntry>? = null,
    val previousStateSimulated: PreviousStateSimulated? = null,
    val expectedTax: BigDecimal? = null,
    val expectedStatus: String? = null,
    val expectedMeiWarning: Boolean? = null,
    val testNote: String? = null,
    val freeText: String? = null,
    val expectedResult: String? = null
)