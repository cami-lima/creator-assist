package br.com.creatorassist.web

import br.com.creatorassist.agents.IncomeClassifierAgent
import br.com.creatorassist.agents.Orchestrator
import br.com.creatorassist.report.ReportFormatter
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ReportRequest(
    val creatorName: String,
    val referenceMonth: String,
    val incomeText: String,
    val contractText: String? = null
)

data class ReportResponse(
    val ambiguous: Boolean,
    val message: String? = null,
    val reportMarkdown: String? = null
)

/**
 * Minimal HTTP entry point for the same pipeline InteractiveRunner
 * exercises from the terminal — same Orchestrator, same agents, same
 * ReportFormatter, just reachable from a browser instead of a
 * console. Serves the form at src/main/resources/static/index.html.
 *
 * Scoped to the default profile only (no --spring.profiles.active
 * flag): both "eval" and "interactive" disable the web server
 * entirely (spring.main.web-application-type: none), so this
 * controller is kept out of those runs to avoid interfering with
 * infrastructure that isn't initialized there.
 */
@RestController
@Profile("default")
class ReportController(
    private val incomeClassifier: IncomeClassifierAgent,
    private val orchestrator: Orchestrator
) {
    @PostMapping("/api/report")
    suspend fun generateReport(@RequestBody request: ReportRequest): ReportResponse {
        val classification = incomeClassifier.classifyFreeText(request.incomeText)

        if (classification.ambiguous) {
            return ReportResponse(
                ambiguous = true,
                message = classification.messageToUser ?: "Informação insuficiente."
            )
        }

        val finalReport = orchestrator.process(
            creatorId = request.creatorName,
            referenceMonth = request.referenceMonth,
            incomeEntries = classification.incomeEntries,
            contractText = request.contractText?.takeIf { it.isNotBlank() }
        )

        val formatted = ReportFormatter.format(
            creatorName = request.creatorName,
            referenceMonth = request.referenceMonth,
            report = finalReport
        )

        return ReportResponse(ambiguous = false, reportMarkdown = formatted)
    }
}