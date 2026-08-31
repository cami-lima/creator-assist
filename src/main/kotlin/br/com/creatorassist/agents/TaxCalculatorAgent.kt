package br.com.creatorassist.agents

import br.com.creatorassist.domain.CalculationStatus
import br.com.creatorassist.domain.IncomeEntry
import br.com.creatorassist.domain.IncomeOrigin
import br.com.creatorassist.domain.TaxResult
import br.com.creatorassist.domain.toBrl
import br.com.creatorassist.trajectory.AgentStep
import br.com.creatorassist.trajectory.TrajectoryLogger
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Agent 2, tax calculation with verification.
 *
 * DELIBERATE DESIGN DECISION: the tax calculation is NOT done by the
 * language model. LLMs make arithmetic mistakes too easily for
 * something with real financial consequences (see the challenge's
 * ground rule 05: consequential actions need to be kept under control).
 * The model is only used where the task is genuinely linguistic -
 * Agent 1 (interpreting free text) and Agent 3 (interpreting contract
 * clauses). Here, both the calculation and the verification are
 * deterministic, testable code.
 *
 * This agent still writes to the trajectory log, even though it makes
 * no LLM call, the challenge asks for a trajectory covering "every
 * agent you used", and "no model call, deterministic code instead" is
 * itself part of the trajectory of how the result was reached.
 */
@Component
class TaxCalculatorAgent(private val trajectoryLogger: TrajectoryLogger) {

    fun calculate(incomeEntries: List<IncomeEntry>): TaxResult {
        val notes = mutableListOf<String>()

        val base = incomeEntries.fold(BigDecimal.ZERO) { acc, entry ->
            if (entry.origin == IncomeOrigin.FOREIGN && entry.exchangeRateOnReceiptDate == null) {
                notes.add("O recebimento '${entry.description}' é do exterior mas não tem cotação de câmbio informada.")
            }
            acc.add(entry.amountInBRL())
        }.setScale(2, RoundingMode.HALF_UP)

        val result: TaxResult

        if (notes.isNotEmpty()) {
            result = TaxResult(
                taxableBase = base,
                taxDue = BigDecimal.ZERO,
                status = CalculationStatus.INSUFFICIENT_DATA,
                notes = notes
            )
        } else {
            val calculatedTax = CarneLeaoTaxTable2026.calculateTax(base)
            val verification = CarneLeaoTaxTable2026.verify(base, calculatedTax)

            result = if (verification.passed) {
                TaxResult(
                    taxableBase = base,
                    taxDue = calculatedTax,
                    status = CalculationStatus.OK
                )
            } else {
                TaxResult(
                    taxableBase = base,
                    taxDue = calculatedTax,
                    status = CalculationStatus.NEEDS_HUMAN_REVIEW,
                    notes = listOf(
                        "A verificação encontrou uma diferença de ${verification.difference.toBrl()} entre o " +
                                "cálculo principal e o recálculo independente. Valor recalculado: " +
                                "${verification.recalculatedTax.toBrl()}. Revisar antes de informar ao usuário."
                    )
                )
            }
        }

        trajectoryLogger.log(
            AgentStep(
                agent = "Agent 2 - Tax calculator",
                systemPrompt = null,
                input = incomeEntries.joinToString("; ") {
                    "${it.description}: ${it.originalAmount} ${it.originalCurrency} (${it.origin})"
                },
                toolUsed = "Deterministic Kotlin calculation (CarneLeaoTaxTable2026.calculateTax + " +
                        "independent verify()), no LLM call, by design",
                output = "taxableBase=${result.taxableBase.toBrl()}, taxDue=${result.taxDue.toBrl()}, " +
                        "status=${result.status}",
                note = if (result.notes.isNotEmpty()) result.notes.joinToString(" | ") else null
            )
        )

        return result
    }
}