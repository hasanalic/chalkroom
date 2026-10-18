package app.chalkroom.server.ai

import app.chalkroom.server.ModelSettings
import com.google.genai.Client
import com.google.genai.errors.ApiException
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** Any AI-side failure: timeout, 429, 5xx, safety block, empty candidates, unparseable JSON. Maps to 502. */
class AiUnavailable(reason: String, cause: Throwable? = null) : RuntimeException(reason, cause)

/** Thin wrapper over the Google Gen AI SDK: one structured-output call, parsed into [T]. */
class GeminiClient(apiKey: String, timeoutMs: Int = 28_000) {

    private val log = LoggerFactory.getLogger(GeminiClient::class.java)

    private val client: Client = Client.builder()
        .apiKey(apiKey)
        .httpOptions(HttpOptions.builder().timeout(timeoutMs).build())
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> generateJson(
        settings: ModelSettings,
        systemPrompt: String,
        userPrompt: String,
        schema: Schema,
        serializer: KSerializer<T>,
    ): T {
        val model = settings.model
        val config = GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            .responseMimeType("application/json")
            .responseSchema(schema)
            .apply { settings.temperature?.let { temperature(it) } }
            .apply { settings.maxOutputTokens?.let { maxOutputTokens(it) } }
            .apply { settings.thinking?.let { thinkingConfig(ThinkingConfig.builder().thinkingLevel(it)) } }
            .build()

        val response = try {
            withContext(Dispatchers.IO) { client.models.generateContent(model, userPrompt, config) }
        } catch (e: ApiException) {
            throw AiUnavailable("api_error ${e.code()} ${e.status()}", e)
        } catch (e: Exception) {
            throw AiUnavailable("call_failed ${e.javaClass.simpleName}", e)
        }

        response.promptFeedback().flatMap { it.blockReason() }.ifPresent {
            throw AiUnavailable("prompt_blocked $it")
        }
        val candidate = response.candidates().orElse(emptyList()).firstOrNull()
            ?: throw AiUnavailable("no_candidates")
        val finish = candidate.finishReason().map(FinishReason::knownEnum).orElse(null)
        if (finish != null && finish != FinishReason.Known.STOP) {
            throw AiUnavailable("finish_reason $finish")
        }
        val text = response.text()?.takeIf { it.isNotBlank() } ?: throw AiUnavailable("empty_text")

        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            log.warn("Schema parse failed for model={}", model)
            throw AiUnavailable("parse_failed", e)
        }
    }
}
