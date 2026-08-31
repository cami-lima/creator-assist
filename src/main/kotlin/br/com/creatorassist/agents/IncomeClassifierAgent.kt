package br.com.creatorassist.agents

import br.com.creatorassist.domain.IncomeClassification
import br.com.creatorassist.domain.IncomeEntry
import br.com.creatorassist.llm.LlmClient
import br.com.creatorassist.llm.stripMarkdownJsonFence
import br.com.creatorassist.trajectory.AgentStep
import br.com.creatorassist.trajectory.TrajectoryLogger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Agent 1 — income ingestion and classification.
 *
 * When income entries already arrive structured (e.g. extracted from a
 * bank statement CSV — see the scope decision in README.md), this agent
 * just organizes them by origin, without needing the model at all. When
 * they arrive as free, potentially ambiguous text, it delegates to the
 * model — which must return "ambiguous": true instead of guessing a
 * value (see test case F6).
 */
@Component
class IncomeClassifierAgent(
    private val llmClient: LlmClient,
    private val trajectoryLogger: TrajectoryLogger
) {
    private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val systemPrompt = ClassPathResource("prompts/income_classifier_system.txt").inputStream
        .bufferedReader().readText()

    fun classifyStructured(incomeEntries: List<IncomeEntry>): IncomeClassification =
        IncomeClassification(ambiguous = false, incomeEntries = incomeEntries)

    suspend fun classifyFreeText(userText: String): IncomeClassification {
        val callerLabel = "Agent 1 - Income classifier"
        val response = llmClient.complete(systemPrompt, userText, maxTokens = 2048, callerLabel = callerLabel)
        trajectoryLogger.log(
            AgentStep(
                agent = callerLabel,
                systemPrompt = systemPrompt,
                input = userText,
                toolUsed = "Gemini API (LlmClient)",
                output = response
            )
        )
        return try {
            mapper.readValue(response.stripMarkdownJsonFence(), IncomeClassification::class.java)
        } catch (e: Exception) {
            // If the model did not return valid JSON, treat it as
            // ambiguous instead of letting the error propagate silently
            // or risking misinterpretation of a malformed response.
            // The real cause is printed here (not hidden) — this is a
            // different failure mode than "the text itself was
            // ambiguous": it means the model's response could not be
            // parsed at all (e.g. truncated JSON, or an unexpected
            // format), which is worth knowing when debugging.
            println("[Agent 1] Could not parse the model's response as JSON. Raw error: ${e.message}")
            println("[Agent 1] Raw response received: ${response.take(500)}")
            IncomeClassification(
                ambiguous = true,
                messageToUser = "Não consegui interpretar a resposta do modelo com segurança " +
                        "(erro técnico, não necessariamente um problema com o texto informado). " +
                        "Tente rodar de novo."
            )
        }
    }
}