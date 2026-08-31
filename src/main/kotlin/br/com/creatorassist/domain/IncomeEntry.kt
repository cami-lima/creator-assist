package br.com.creatorassist.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class IncomeOrigin {
    DOMESTIC,
    FOREIGN,
    BARTER
}

data class IncomeEntry(
    val description: String,
    val origin: IncomeOrigin,
    val originalAmount: BigDecimal,
    val originalCurrency: String = "BRL",
    val exchangeRateOnReceiptDate: BigDecimal? = null,
    val receiptDate: LocalDate
) {
    /** Amount already converted to BRL, ready to compose the taxable base. */
    fun amountInBRL(): BigDecimal =
        if (originalCurrency == "BRL") originalAmount
        else originalAmount.multiply(
            exchangeRateOnReceiptDate
                ?: error("Missing exchange rate for foreign income: '$description'")
        )
}
