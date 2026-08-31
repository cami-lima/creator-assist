package br.com.creatorassist.trajectory

import java.time.Instant

data class AgentStep(
    val agent: String,
    val timestamp: Instant = Instant.now(),
    val systemPrompt: String? = null,
    val input: String,
    val toolUsed: String? = null,
    val output: String,
    val note: String? = null
)
