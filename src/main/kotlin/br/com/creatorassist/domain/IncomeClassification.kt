package br.com.creatorassist.domain

data class IncomeClassification(
    val ambiguous: Boolean,
    val messageToUser: String? = null,
    val incomeEntries: List<IncomeEntry> = emptyList()
)
