package br.com.creatorassist.agents

import br.com.creatorassist.domain.ContractAnalysisResult
import br.com.creatorassist.domain.IncomeEntry
import br.com.creatorassist.domain.TaxResult
import br.com.creatorassist.domain.toBrl
import br.com.creatorassist.memory.CreatorMonthState
import br.com.creatorassist.memory.CreatorMonthStateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Component
import java.math.BigDecimal

data class FinalReport(
    val tax: TaxResult,
    val contract: ContractAnalysisResult?,
    val meiLimitWarning: String? = null
)

/**
 * Orchestrates the two independent branches (tax and contract) from
 * the same input, running them in parallel, and reads/writes the
 * cross-month memory. Splitting into two branches is deliberate: these
 * are tasks with completely different tools and success criteria, so a
 * single agent doing both would tend to dilute the quality of each
 * part.
 */
@Component
class Orchestrator(
    private val incomeClassifier: IncomeClassifierAgent,
    private val taxCalculator: TaxCalculatorAgent,
    private val contractAnalyst: ContractAnalystAgent,
    private val memoryRepository: CreatorMonthStateRepository
) {
    private val meiRevenueCap = BigDecimal("81000.00")

    suspend fun process(
        creatorId: String,
        referenceMonth: String,
        incomeEntries: List<IncomeEntry>,
        contractText: String?
    ): FinalReport = coroutineScope {
        val classification = incomeClassifier.classifyStructured(incomeEntries)

        val taxDeferred = async { taxCalculator.calculate(classification.incomeEntries) }
        val contractDeferred = contractText?.let { text -> async { contractAnalyst.analyze(text) } }

        val taxResult = taxDeferred.await()
        val contractResult = contractDeferred?.await()

        val previousState = memoryRepository.findTopByCreatorIdOrderByReferenceMonthDesc(creatorId)
        val yearToDateRevenue = (previousState?.yearToDateRevenue ?: BigDecimal.ZERO)
            .add(taxResult.taxableBase)

        val limitWarning = if (previousState?.taxRegime == "MEI" && yearToDateRevenue > meiRevenueCap) {
            "Atenção: o faturamento acumulado no ano (${yearToDateRevenue.toBrl()}) ultrapassa o teto do MEI. " +
                    "Recomendamos buscar orientação contábil sobre a mudança de regime antes de continuar."
        } else null

        memoryRepository.save(
            CreatorMonthState(
                creatorId = creatorId,
                referenceMonth = referenceMonth,
                taxRegime = previousState?.taxRegime ?: "SELF_EMPLOYED",
                yearToDateRevenue = yearToDateRevenue,
                taxPaidThisMonth = taxResult.taxDue
            )
        )

        FinalReport(
            tax = taxResult,
            contract = contractResult,
            meiLimitWarning = limitWarning
        )
    }
}