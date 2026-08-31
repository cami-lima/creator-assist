package br.com.creatorassist.baseline

import br.com.creatorassist.llm.LlmClient
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Baseline: a single generic prompt, with no tools, no memory, no
 * verification, and no specialized skill. It receives exactly the same
 * task (income + contract) as the final solution, to allow a fair
 * comparison on the same test cases.
 */
@Component
class BaselineSolver(private val llmClient: LlmClient) {
    private val systemPrompt = ClassPathResource("prompts/baseline_system.txt").inputStream
        .bufferedReader().readText()

    suspend fun solve(incomeDescription: String, contractText: String?): String {
        val input = buildString {
            appendLine("Month's income:")
            appendLine(incomeDescription)
            if (contractText != null) {
                appendLine()
                appendLine("Contract:")
                appendLine(contractText)
            }
        }
        return llmClient.complete(systemPrompt, input, maxTokens = 2048, callerLabel = "Baseline")
    }
}
