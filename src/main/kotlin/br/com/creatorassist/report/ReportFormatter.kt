package br.com.creatorassist.report

import br.com.creatorassist.agents.FinalReport
import br.com.creatorassist.domain.CalculationStatus
import br.com.creatorassist.domain.RiskSeverity
import br.com.creatorassist.domain.toBrl

/**
 * Formats a FinalReport into the document an actual Brazilian content
 * creator would read, plain Portuguese, no JSON, no PASS/FAIL, no
 * developer jargon. This is deliberately separate from
 * evaluation-report.md (which is for judges), this is what the real
 * end user of the product would see.
 */
object ReportFormatter {

    private fun severityLabel(severity: RiskSeverity): String = when (severity) {
        RiskSeverity.HIGH -> "ALTA"
        RiskSeverity.MEDIUM -> "MÉDIA"
        RiskSeverity.LOW -> "BAIXA"
    }

    fun format(creatorName: String, referenceMonth: String, report: FinalReport): String {
        val sb = StringBuilder()

        sb.appendLine("# Relatório do mês - $creatorName")
        sb.appendLine()
        sb.appendLine("**Mês de referência:** $referenceMonth")
        sb.appendLine()
        sb.appendLine("## Imposto (Carnê-Leão)")
        sb.appendLine()
        sb.appendLine("- Base tributável do mês: ${report.tax.taxableBase.toBrl()}")
        sb.appendLine("- Imposto devido: ${report.tax.taxDue.toBrl()}")

        when (report.tax.status) {
            CalculationStatus.OK ->
                sb.appendLine("- Status: cálculo conferido por dupla verificação, sem pendências.")
            CalculationStatus.NEEDS_HUMAN_REVIEW ->
                sb.appendLine(
                    "- Status: ATENÇÃO. A verificação encontrou uma divergência neste cálculo. " +
                            "Não utilize este valor sem revisão de um contador."
                )
            CalculationStatus.INSUFFICIENT_DATA ->
                sb.appendLine("- Status: dados insuficientes para calcular com segurança.")
        }

        report.tax.notes.forEach { sb.appendLine("- Observação: $it") }

        report.meiLimitWarning?.let { warning ->
            sb.appendLine()
            sb.appendLine("### Alerta: limite do MEI")
            sb.appendLine(warning)
        }

        report.contract?.let { contract ->
            sb.appendLine()
            sb.appendLine("## Análise do contrato de patrocínio")
            sb.appendLine()
            if (contract.riskClauses.isEmpty()) {
                sb.appendLine("Nenhuma cláusula de risco foi identificada neste contrato.")
            } else {
                contract.riskClauses.forEach { clause ->
                    sb.appendLine("### Risco ${severityLabel(clause.severity)}: ${clause.riskType}")
                    sb.appendLine("> \"${clause.originalExcerpt}\"")
                    sb.appendLine()
                    sb.appendLine(clause.explanation)
                    sb.appendLine()
                }
            }
            sb.appendLine("**Resumo geral:** ${contract.overallSummary}")
        }

        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine(
            "_Este relatório é um primeiro parecer gerado automaticamente. Não substitui " +
                    "a revisão de um contador ou advogado antes de qualquer decisão._"
        )

        return sb.toString()
    }
}