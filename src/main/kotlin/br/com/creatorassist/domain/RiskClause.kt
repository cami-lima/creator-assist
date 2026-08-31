package br.com.creatorassist.domain

enum class RiskSeverity { LOW, MEDIUM, HIGH }

data class RiskClause(
    val originalExcerpt: String,
    val riskType: String,
    val severity: RiskSeverity,
    val explanation: String
)

data class ContractAnalysisResult(
    val riskClauses: List<RiskClause>,
    val overallSummary: String
)
