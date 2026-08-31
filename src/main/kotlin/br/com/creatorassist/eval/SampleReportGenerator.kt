package br.com.creatorassist.eval

import br.com.creatorassist.agents.Orchestrator
import br.com.creatorassist.domain.IncomeEntry
import br.com.creatorassist.domain.IncomeOrigin
import br.com.creatorassist.memory.CreatorMonthState
import br.com.creatorassist.memory.CreatorMonthStateRepository
import br.com.creatorassist.report.ReportFormatter
import br.com.creatorassist.trajectory.TrajectoryLogger
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Generates ONE polished, Portuguese, end-user-facing sample report -
 * the document a real content creator would actually receive, by
 * running the real pipeline on real data (T5's income profile,
 * combined with C3's contract, the hard case). This is separate from
 * evaluation-report.md, which is written for judges, not end users.
 *
 * NOTE ON EXECUTION ORDER: this and EvalRunner are two separate
 * CommandLineRunner beans, and Spring does not guarantee which one
 * runs first. TrajectoryLogger is a shared singleton, so whichever of
 * the two runs LAST ends up holding the complete, accumulated list -
 * that is why this class also flushes to the same trajectory file
 * (same executionId as EvalRunner), instead of only relying on
 * EvalRunner to do it. Without this, entries logged here would
 * silently vanish whenever this class happens to run after EvalRunner
 * already wrote the file.
 */
@Component
@Profile("eval")
class SampleReportGenerator(
    private val orchestrator: Orchestrator,
    private val memoryRepository: CreatorMonthStateRepository,
    private val trajectoryLogger: TrajectoryLogger
) : CommandLineRunner {

    override fun run(vararg args: String?) = runBlocking {
        val creatorId = "demo-creator"

        memoryRepository.save(
            CreatorMonthState(
                creatorId = creatorId,
                referenceMonth = "2026-05",
                taxRegime = "MEI",
                yearToDateRevenue = BigDecimal("76000.00"),
                taxPaidThisMonth = BigDecimal.ZERO
            )
        )

        val incomeEntries = listOf(
            IncomeEntry(
                description = "Pagamentos diversos via Pix",
                origin = IncomeOrigin.DOMESTIC,
                originalAmount = BigDecimal("9000.00"),
                originalCurrency = "BRL",
                receiptDate = LocalDate.of(2026, 6, 10)
            )
        )

        val contractText = ClassPathResource("contracts/contract_c3_disguised_exclusivity.md")
            .inputStream.bufferedReader().readText()
            .let { text -> stripReviewerNote(text) }

        val finalReport = orchestrator.process(
            creatorId = creatorId,
            referenceMonth = "2026-06",
            incomeEntries = incomeEntries,
            contractText = contractText
        )

        val formatted = ReportFormatter.format(
            creatorName = "Ana (nome fictício)",
            referenceMonth = "Junho de 2026",
            report = finalReport
        )

        File("sample-user-report.md").writeText(formatted)
        println("Sample user-facing report written to: sample-user-report.md")

        // Flush again here (same executionId as EvalRunner) so this
        // run's Agent 2/Agent 3 entries are captured on disk no matter
        // which of the two CommandLineRunners Spring happened to run
        // last.
        trajectoryLogger.flushToFile("eval-run")
    }

    private fun stripReviewerNote(text: String): String {
        val marker = "---"
        val idx = text.indexOf(marker)
        return if (idx >= 0) text.substring(idx + marker.length).trim() else text.trim()
    }
}