package app.chalkroom.server.evals

import app.chalkroom.server.Config
import app.chalkroom.server.ai.AiUnavailable
import app.chalkroom.server.ai.GeminiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Shared plumbing for the real-API evals. Gradle loads GEMINI_API_KEY from the root `.env`. */
object EvalSupport {
    val config: Config by lazy { Config.fromEnv() }

    val gemini: GeminiClient by lazy { GeminiClient(config.geminiApiKey, timeoutMs = 60_000) }

    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** Keep a few calls in flight; enough to be quick, low enough to stay under per-minute quotas. */
    private val permits = Semaphore(4)

    fun assumeKey() = assumeTrue(
        !System.getenv("GEMINI_API_KEY").isNullOrBlank(),
        "GEMINI_API_KEY not set; skipping real-API evals",
    )

    inline fun <reified T> loadCases(name: String): List<T> {
        val text = requireNotNull(EvalSupport::class.java.getResource("/evals/cases/$name")) { "missing $name" }.readText()
        return json.decodeFromString(text)
    }

    /** Retries only infrastructure hiccups (quota, overload), never a model's answer. */
    suspend fun <T> withRetry(block: suspend () -> T): T {
        var wait = 5_000L
        repeat(3) {
            try {
                return permits.withPermit { block() }
            } catch (e: AiUnavailable) {
                val transient = e.message.orEmpty().let { it.startsWith("api_error 429") || it.startsWith("api_error 50") || it.startsWith("call_failed") }
                if (!transient) throw e
                delay(wait)
                wait *= 2
            }
        }
        return permits.withPermit { block() }
    }

    suspend fun <A, B> List<A>.parallelMap(f: suspend (A) -> B): List<B> = coroutineScope {
        map { async { f(it) } }.awaitAll()
    }

    fun String.oneLine(max: Int = 140): String =
        replace("|", "/").replace("\n", " ").let { if (it.length > max) it.take(max - 1) + "…" else it }
}
