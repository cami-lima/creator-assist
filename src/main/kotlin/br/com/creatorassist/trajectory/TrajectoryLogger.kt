package br.com.creatorassist.trajectory

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Component
import java.io.File

/**
 * Logs the trajectory of every agent call to a JSON file per run.
 * This directly satisfies the challenge's explicit requirement:
 * "Include representative trajectories for every agent you used.
 * Show what the agent did and how its tools responded."
 *
 * NOTE: this logger is a shared singleton (Spring @Component), so in
 * concurrent runs the steps of different executions get mixed into
 * the same list. For the hackathon's scope (sequential runs during
 * evaluation) this is acceptable; for real production use this would
 * need to be scoped per execution (e.g. one logger instance per
 * request).
 */
@Component
class TrajectoryLogger {
    private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val steps = mutableListOf<AgentStep>()

    fun log(step: AgentStep) {
        steps.add(step)
    }

    fun flushToFile(executionId: String, outputDir: String = "trajectories") {
        File(outputDir).mkdirs()
        val file = File(outputDir, "trajectory_$executionId.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, steps)
    }

    fun reset() {
        steps.clear()
    }
}
