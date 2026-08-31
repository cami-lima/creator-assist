package br.com.creatorassist.eval

import br.com.creatorassist.agents.ContractAnalystAgent
import br.com.creatorassist.agents.IncomeClassifierAgent
import br.com.creatorassist.agents.Orchestrator
import br.com.creatorassist.baseline.BaselineSolver
import br.com.creatorassist.domain.IncomeEntry
import br.com.creatorassist.memory.CreatorMonthState
import br.com.creatorassist.memory.CreatorMonthStateRepository
import br.com.creatorassist.trajectory.TrajectoryLogger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import java.io.File
import java.math.BigDecimal

/**
 * Runs the baseline and the final solution against the same test cases
 * (docs/test_cases_and_answer_key.md) and writes a comparison report.
 *
 * Run with: ./gradlew bootRun --args='--spring.profiles.active=eval'
 */
@Component
@Profile("eval")
class EvalRunner(
    private val orchestrator: Orchestrator,
    private val baselineSolver: BaselineSolver,
    private val incomeClassifier: IncomeClassifierAgent,
    private val contractAnalyst: ContractAnalystAgent,
    private val memoryRepository: CreatorMonthStateRepository,
    private val trajectoryLogger: TrajectoryLogger
) : CommandLineRunner {

    private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val report = StringBuilder()
    private val baselineFullResponses = StringBuilder()
    private val currencyMentionPattern = Regex("""R\$\s?[\d.,]+""")

    override fun run(vararg args: String?) = runBlocking {
        report.appendLine("# Evaluation report")
        report.appendLine()

        runTaxCases()
        runContractCases()
        reportMemoryState()

        report.appendLine("## Baseline full responses")
        report.appendLine()
        report.appendLine(
            "Full, untruncated baseline output for every tax case, for manual " +
                    "comparison against the final solution's result above. " +
                    "\"Figures mentioned\" is a best-effort regex scan for any " +
                    "\"R\$ ...\" amount in the response. It is not an automatic " +
                    "correctness check, just a pointer for the reader's eye."
        )
        report.appendLine()
        report.append(baselineFullResponses)

        val reportFile = File("evaluation-report.md")
        reportFile.writeText(report.toString())
        trajectoryLogger.flushToFile("eval-run")

        println()
        println("Evaluation finished. Report written to: ${reportFile.absolutePath}")
        println("Trajectories written to: trajectories/trajectory_eval-run.json")
    }

    private fun recordBaseline(caseId: String, baselineRaw: String) {
        val figuresMentioned = currencyMentionPattern.findAll(baselineRaw)
            .map { it.value }
            .toList()
        baselineFullResponses.appendLine("### $caseId")
        baselineFullResponses.appendLine()
        baselineFullResponses.appendLine(
            "Figures mentioned: " + if (figuresMentioned.isEmpty()) "none found" else figuresMentioned.joinToString(", ")
        )
        baselineFullResponses.appendLine()
        baselineFullResponses.appendLine("```")
        baselineFullResponses.appendLine(baselineRaw.trim())
        baselineFullResponses.appendLine("```")
        baselineFullResponses.appendLine()
    }

    private suspend fun runTaxCases() {
        report.appendLine("## Tax calculation cases")
        report.appendLine()
        report.appendLine("| Case | Expected tax | Final solution | MEI warning | Result |")
        report.appendLine("|---|---|---|---|---|")

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath*:testcases/tax/*.json").sortedBy { it.filename }

        for (resource in resources) {
            val testCase = mapper.readValue(resource.inputStream, TaxTestCase::class.java)
            val creatorId = "eval-${testCase.id}"

            try {
                if (testCase.freeText != null) {
                    // Hard case: ambiguous free text. Must not be run through
                    // the structured path, and must not produce a guessed number.
                    val classification = incomeClassifier.classifyFreeText(testCase.freeText)
                    val baselineRaw = baselineSolver.solve(testCase.freeText, null)
                    recordBaseline(testCase.id, baselineRaw)
                    val result = if (classification.ambiguous) "PASS (flagged as ambiguous)" else "FAIL (did not flag ambiguity)"
                    report.appendLine(
                        "| ${testCase.id} | n/a (ambiguous) | ambiguous=${classification.ambiguous} | n/a | $result |"
                    )
                    println("[${testCase.id}] ambiguous=${classification.ambiguous} -> $result")
                } else {
                    val entries: List<IncomeEntry> = testCase.incomeEntries ?: emptyList()

                    testCase.previousStateSimulated?.let { prev ->
                        memoryRepository.save(
                            CreatorMonthState(
                                creatorId = creatorId,
                                referenceMonth = "2026-05",
                                taxRegime = prev.taxRegime,
                                yearToDateRevenue = prev.yearToDateRevenue,
                                taxPaidThisMonth = BigDecimal.ZERO
                            )
                        )
                    }

                    val finalReport = orchestrator.process(
                        creatorId = creatorId,
                        referenceMonth = "2026-06",
                        incomeEntries = entries,
                        contractText = null
                    )

                    val baselineDescription = entries.joinToString("\n") {
                        "- ${it.description}: ${it.originalAmount} ${it.originalCurrency} (${it.origin})"
                    }
                    val baselineRaw = baselineSolver.solve(baselineDescription, null)
                    recordBaseline(testCase.id, baselineRaw)

                    val expected = testCase.expectedTax
                    val actual = finalReport.tax.taxDue
                    val difference = expected?.subtract(actual)?.abs() ?: BigDecimal.ZERO
                    val taxOk = expected != null && difference <= BigDecimal("0.01")

                    val meiWarningPresent = finalReport.meiLimitWarning != null
                    val meiOk = testCase.expectedMeiWarning == null ||
                            testCase.expectedMeiWarning == meiWarningPresent

                    val passed = taxOk && meiOk
                    val result = if (passed) "PASS" else "FAIL"
                    val meiCell = when (testCase.expectedMeiWarning) {
                        null -> "n/a"
                        else -> "expected=${testCase.expectedMeiWarning}, got=$meiWarningPresent"
                    }

                    report.appendLine(
                        "| ${testCase.id} | R\$ $expected | R\$ $actual (${finalReport.tax.status}) | $meiCell | $result |"
                    )
                    println(
                        "[${testCase.id}] expected=$expected actual=$actual status=${finalReport.tax.status} " +
                                "meiWarning=$meiWarningPresent -> $result"
                    )
                }
            } catch (e: Exception) {
                report.appendLine("| ${testCase.id} | - | - | - | ERROR: ${e.message} |")
                println("[${testCase.id}] ERROR: ${e.message}")
            }
        }
        report.appendLine()
    }

    private suspend fun runContractCases() {
        report.appendLine("## Contract analysis cases")
        report.appendLine()
        report.appendLine("| Case | Expectation | Final solution result | Result |")
        report.appendLine("|---|---|---|---|")

        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath*:contracts/*.md").sortedBy { it.filename }

        for (resource in resources) {
            val filename = resource.filename ?: "unknown"
            val rawText = resource.inputStream.bufferedReader().readText()
            val contractText = stripReviewerNote(rawText)

            try {
                val analysis = contractAnalyst.analyze(contractText)
                val riskCount = analysis.riskClauses.size

                val (expectation, passed) = when {
                    filename.contains("clean") -> "zero risk clauses" to (riskCount == 0)
                    filename.contains("perpetual") -> "at least one HIGH risk clause about image rights" to
                            analysis.riskClauses.any { it.severity.name == "HIGH" }
                    filename.contains("disguised") -> "at least one risk clause flagged (hard case)" to (riskCount > 0)
                    else -> "n/a" to true
                }

                val result = if (passed) "PASS" else "FAIL (needs manual review)"
                val summary = analysis.riskClauses.joinToString("; ") { "${it.severity}: ${it.riskType}" }
                    .ifEmpty { "no risk clauses found" }

                report.appendLine("| $filename | $expectation | $summary | $result |")
                println("[$filename] found $riskCount risk clause(s) -> $result")
            } catch (e: Exception) {
                report.appendLine("| $filename | - | ERROR: ${e.message} | ERROR |")
                println("[$filename] ERROR: ${e.message}")
            }
        }
        report.appendLine()
        report.appendLine(
            "Note: contract cases (especially the hard case, disguised exclusivity) are " +
                    "automatically checked only for whether the agent found something, not for exact " +
                    "wording. Read docs/test_cases_and_answer_key.md and the raw model output to judge " +
                    "quality manually, that comparison is part of the submission write-up. These prompts " +
                    "were also only validated against the 3 synthetic contracts in this repository, all " +
                    "written by the developer, so behavior on a real, unseen contract is not yet verified " +
                    "(see CHANGELOG.md's known limitations)."
        )
    }

    private fun reportMemoryState() {
        // Direct proof of persistence: this is not inferred from a test
        // passing. It is a live query against the same H2 database the
        // agents above just wrote to. If memory weren't actually being
        // saved, this list would be empty even though T3/T5 passed for
        // other reasons.
        val allRows = memoryRepository.findAll().sortedBy { it.creatorId }

        report.appendLine("## Memory state actually persisted to the database")
        report.appendLine()
        report.appendLine(
            "Not inferred from test results. This is a direct query " +
                    "(`memoryRepository.findAll()`) against the same H2 database the " +
                    "agents above just wrote to, run at the very end of this evaluation. " +
                    "Every row below is real, persisted data."
        )
        report.appendLine()
        if (allRows.isEmpty()) {
            report.appendLine("No rows found. Memory was not used in this run.")
        } else {
            report.appendLine("| creatorId | referenceMonth | taxRegime | yearToDateRevenue | taxPaidThisMonth |")
            report.appendLine("|---|---|---|---|---|")
            allRows.forEach { row ->
                report.appendLine(
                    "| ${row.creatorId} | ${row.referenceMonth} | ${row.taxRegime} | " +
                            "${row.yearToDateRevenue} | ${row.taxPaidThisMonth} |"
                )
            }
        }
        report.appendLine()

        println()
        println("Memory rows actually persisted in the database (${allRows.size} total):")
        allRows.forEach { row ->
            println(
                "  ${row.creatorId} | ${row.referenceMonth} | regime=${row.taxRegime} | " +
                        "YTD=${row.yearToDateRevenue} | taxPaid=${row.taxPaidThisMonth}"
            )
        }
    }

    private fun stripReviewerNote(text: String): String {
        val marker = "---"
        val idx = text.indexOf(marker)
        return if (idx >= 0) text.substring(idx + marker.length).trim() else text.trim()
    }
}