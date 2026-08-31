package br.com.creatorassist.llm

/**
 * Small abstraction so agents don't depend on a specific model
 * provider. This project ships with GeminiClient wired as the active
 * implementation (Google's free tier, no credit card required). If you
 * later have Anthropic API credits, ClaudeClient already implements
 * this same interface — see the note in README.md on how to switch.
 *
 * "callerLabel" identifies which agent is making the call (e.g.
 * "Agent 1 - Income classifier"), so that if a retry happens inside
 * the client (see GeminiClient), it can be attributed to the right
 * agent in the trajectory log.
 */
interface LlmClient {
    suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 1024,
        callerLabel: String = "unknown"
    ): String
}
