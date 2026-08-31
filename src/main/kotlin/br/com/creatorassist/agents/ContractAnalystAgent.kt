package br.com.creatorassist.agents

import br.com.creatorassist.domain.ContractAnalysisResult
import br.com.creatorassist.llm.LlmClient
import br.com.creatorassist.llm.stripMarkdownJsonFence
import br.com.creatorassist.trajectory.AgentStep
import br.com.creatorassist.trajectory.TrajectoryLogger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Agent 3 — sponsorship contract analysis.
 *
 * This agent's "skill" is the system prompt in
 * resources/prompts/contract_analyst_system.txt, which explicitly
 * lists the risk criteria (see test cases C1-C5 in docs/). That
 * specialization is what separates a surface-level reading (searching
 * for the literal word "exclusivity") from correctly flagging the hard
 * case C5, where exclusivity is disguised as a permissive clause.
 */
@Component
class ContractAnalystAgent(
    private val llmClient: LlmClient,
    private val trajectoryLogger: TrajectoryLogger
) {
    private val mapper = jacksonObjectMapper()
    private val systemPrompt = ClassPathResource("prompts/contract_analyst_system.txt").inputStream
        .bufferedReader().readText()

    suspend fun analyze(contractText: String): ContractAnalysisResult {
        val callerLabel = "Agent 3 - Contract analyst"
        val response = llmClient.complete(systemPrompt, contractText, maxTokens = 2048, callerLabel = callerLabel)
        trajectoryLogger.log(
            AgentStep(
                agent = callerLabel,
                systemPrompt = systemPrompt,
                input = contractText.take(500) +
                        if (contractText.length > 500) "... [truncated in log]" else "",
                toolUsed = "Gemini API (LlmClient)",
                output = response
            )
        )
        return mapper.readValue(response.stripMarkdownJsonFence(), ContractAnalysisResult::class.java)
    }
}