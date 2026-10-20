package app.chalkroom.server

import app.chalkroom.server.ai.AiUnavailable
import app.chalkroom.server.ai.GeminiClient
import app.chalkroom.server.ai.MisconceptionGenerator
import app.chalkroom.server.ai.GeneratedDeck
import app.chalkroom.server.ai.JudgeVerdict
import app.chalkroom.server.ai.TurnInput
import app.chalkroom.server.ai.TurnJudge
import app.chalkroom.shared.ErrorBody
import app.chalkroom.shared.ErrorCodes
import app.chalkroom.shared.StudentId
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

private val log = LoggerFactory.getLogger("app.chalkroom.server")

fun main() {
    val config = Config.fromEnv()
    if (config.geminiApiKey.isBlank()) log.warn("GEMINI_API_KEY is not set; AI calls will fail with 502")
    log.info("Models: gen={} turn={}", config.gen, config.turn)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: Config) {
    val gemini = GeminiClient(config.geminiApiKey)
    val generator = MisconceptionGenerator(gemini, config.gen)
    val judge = TurnJudge(gemini, config.turn)
    serve(generate = generator::generate, judge = judge::judge)
}

/** Everything but the AI clients, so tests can drive the real routes with stand-ins. */
fun Application.serve(
    generate: suspend (notes: String, studentId: StudentId) -> GeneratedDeck,
    judge: suspend (TurnInput) -> JudgeVerdict,
) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = true }) }
    install(CallLogging)
    install(StatusPages) {
        exception<AiUnavailable> { call, cause ->
            log.warn("AI unavailable: {}", cause.message)
            call.respond(HttpStatusCode.BadGateway, ErrorBody(ErrorCodes.AI_UNAVAILABLE))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody(ErrorCodes.BAD_REQUEST))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorBody(ErrorCodes.RATE_LIMITED))
        }
    }
    install(RateLimit) {
        register(DecksLimit) {
            rateLimiter(limit = 20, refillPeriod = 1.hours)
            requestKey { it.clientIp() }
        }
        register(TurnsLimit) {
            rateLimiter(limit = 150, refillPeriod = 1.hours)
            requestKey { it.clientIp() }
        }
    }
    configureRoutes(generate, judge)
}

/** Railway sits behind a proxy, so prefer the first X-Forwarded-For hop. */
private fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteHost
