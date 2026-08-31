package br.com.creatorassist.llm

/**
 * LLMs are frequently instructed to "respond only in JSON, no text
 * before or after" and still wrap the response in a markdown code
 * fence (```json ... ```) anyway. This happened during real testing
 * of this project (Agent 1's classifier). This strips that fence if
 * present, so JSON parsing doesn't break on a habit the model has that
 * the prompt alone doesn't reliably prevent. Used by every agent that
 * parses a structured response from an LlmClient.
 */
fun String.stripMarkdownJsonFence(): String {
    val trimmed = this.trim()
    return if (trimmed.startsWith("```")) {
        trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    } else {
        trimmed
    }
}