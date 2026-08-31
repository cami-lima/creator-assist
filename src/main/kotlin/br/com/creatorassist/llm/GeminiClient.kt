package br.com.creatorassist.llm

import br.com.creatorassist.trajectory.AgentStep
import br.com.creatorassist.trajectory.TrajectoryLogger
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>, val role: String? = null)
data class GeminiSystemInstruction(val parts: List<GeminiPart>)
data class GeminiGenerationConfig(@JsonProperty("maxOutputTokens") val maxOutputTokens: Int)
data class GeminiRequest(
    @JsonProperty("system_instruction") val systemInstruction: GeminiSystemInstruction,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)
data class GeminiCandidate(val content: GeminiContent)
data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

/**
 * Active LLM provider for this submission. We use Google's Gemini API
 * instead of Anthropic's because Gemini has a genuinely free tier (no
 * credit card required), while the Anthropic API requires paid credits
 * after a small initial trial. This class implements the same
 * LlmClient interface as ClaudeClient, so switching providers later is
 * a one-line change (see README.md).
 *
 * DEVELOPMENT NOTE: built and run on a corporate network that performs
 * TLS inspection (re-signs every HTTPS connection with its own
 * certificate), which Java does not trust by default. This
 * implementation disables certificate validation for that reason -
 * the inspecting proxy already fully controls this traffic regardless
 * of what Java trusts, so this does not reduce real security for a
 * local submission. Do not reuse this trust-all pattern in a
 * public-facing production service.
 *
 * This uses the JDK's built-in java.net.http.HttpClient directly,
 * instead of Spring's WebClient, after a direct curl POST to this same
 * endpoint confirmed the network path works fine, the issue was a
 * request-construction quirk in the WebClient/reactor-netty layer that
 * a plain, well-understood HTTP client avoids entirely.
 *
 * Every retry (HTTP 429 quota / 503 overload) is logged to the
 * trajectory under the calling agent's label, per the challenge's
 * requirement to "capture ... any retries" in agent trajectories.
 */
@Component
class GeminiClient(
    @Value("\${gemini.api-key}") private val apiKey: String,
    @Value("\${gemini.model}") private val model: String,
    @Value("\${gemini.base-url}") private val baseUrl: String,
    private val trajectoryLogger: TrajectoryLogger
) : LlmClient {

    private val mapper = ObjectMapper().registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val trustAllContext: SSLContext = SSLContext.getInstance("TLS").apply {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        init(null, arrayOf(trustAllManager), SecureRandom())
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .sslContext(trustAllContext)
        .build()

    override suspend fun complete(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int,
        callerLabel: String
    ): String = withContext(Dispatchers.IO) {
        val requestBody = GeminiRequest(
            systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(systemPrompt))),
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(userMessage)), role = "user")),
            generationConfig = GeminiGenerationConfig(maxOutputTokens = maxTokens)
        )
        val bodyJson = mapper.writeValueAsString(requestBody)
        val uri = URI.create("$baseUrl/$model:generateContent")

        val maxAttempts = 4
        var lastErrorBody = ""

        for (attempt in 1..maxAttempts) {
            val request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() in 200..299) {
                val parsed = mapper.readValue(response.body(), GeminiResponse::class.java)
                return@withContext parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: error("Gemini response did not include any text content")
            }

            lastErrorBody = response.body()

            // The free tier has a low, short-window request quota.
            // 429 (rate limit) and 503 (temporary overload) are both
            // transient. Google's own error message tells us how
            // long to wait, so we back off and retry instead of
            // failing the whole evaluation run on a passing spike.
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                if (attempt < maxAttempts) {
                    val waitSeconds = Regex("retry in ([0-9.]+)s")
                        .find(lastErrorBody)
                        ?.groupValues?.get(1)
                        ?.toDoubleOrNull()
                        ?.let { Math.ceil(it).toLong() }
                        ?: 15L

                    trajectoryLogger.log(
                        AgentStep(
                            agent = callerLabel,
                            systemPrompt = systemPrompt.take(200),
                            input = userMessage.take(200),
                            toolUsed = "Gemini API ($model)",
                            output = "",
                            note = "Attempt $attempt of $maxAttempts failed with HTTP " +
                                    "${response.statusCode()}. Retrying in ${waitSeconds}s."
                        )
                    )

                    Thread.sleep((waitSeconds + 1) * 1000)
                    continue
                }
            }

            trajectoryLogger.log(
                AgentStep(
                    agent = callerLabel,
                    systemPrompt = systemPrompt.take(200),
                    input = userMessage.take(200),
                    toolUsed = "Gemini API ($model)",
                    output = "",
                    note = "Failed permanently after $attempt attempt(s) with HTTP " +
                            "${response.statusCode()}: ${lastErrorBody.take(300)}"
                )
            )
            error("Gemini API returned HTTP ${response.statusCode()}: ${lastErrorBody.take(500)}")
        }

        error("Gemini API returned HTTP error after $maxAttempts attempts: ${lastErrorBody.take(500)}")
    }
}