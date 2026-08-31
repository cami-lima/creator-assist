package br.com.creatorassist.llm

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

data class ClaudeMessage(val role: String, val content: String)

data class ClaudeRequest(
    val model: String,
    @JsonProperty("max_tokens") val maxTokens: Int = 1024,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)

data class ClaudeContentBlock(val type: String, val text: String? = null)
data class ClaudeResponse(val content: List<ClaudeContentBlock>)

/**
 * Thin wrapper around the Anthropic API. NOT wired as an active Spring
 * bean right now (no @Component) because this submission runs on
 * GeminiClient instead. The Anthropic API requires paid credits beyond
 * a small initial trial, while Gemini has a genuinely free tier.
 *
 * To switch back to Claude once credits are available: add
 * "@Component" above the class declaration and remove it from
 * GeminiClient (only one LlmClient bean can be active at a time).
 * Everything else (agents, prompts) stays exactly the same, since both
 * clients implement the same LlmClient interface.
 */
class ClaudeClient(
    @Value("\${anthropic.api-key}") private val apiKey: String,
    @Value("\${anthropic.model}") private val model: String,
    @Value("\${anthropic.base-url}") private val baseUrl: String
) : LlmClient {
    private val client = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .defaultHeader("content-type", "application/json")
        .build()

    override suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        callerLabel: String
    ): String {
        val request = ClaudeRequest(
            model = model,
            maxTokens = maxTokens,
            system = systemPrompt,
            messages = listOf(ClaudeMessage(role = "user", content = userMessage))
        )
        val response = client.post()
            .uri("")
            .bodyValue(request)
            .retrieve()
            .awaitBody<ClaudeResponse>()
        return response.content.firstOrNull { it.type == "text" }?.text
            ?: error("Model response did not include a text block")
    }
}