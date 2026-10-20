package app.chalkroom.server

import app.chalkroom.server.ai.AiUnavailable
import app.chalkroom.server.ai.GeneratedDeck
import app.chalkroom.server.ai.JudgeVerdict
import app.chalkroom.server.ai.TurnInput
import app.chalkroom.shared.CreateDeckRequest
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.ErrorBody
import app.chalkroom.shared.ErrorCodes
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnLimits
import app.chalkroom.shared.TurnRequest
import app.chalkroom.shared.TurnResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

val DecksLimit = RateLimitName("decks")
val TurnsLimit = RateLimitName("turns")

/** Statement and refutation come back from the app; cap them so the endpoint can't carry arbitrary payloads. */
private const val MAX_ITEM_CHARS = 2_000

fun Application.configureRoutes(
    generate: suspend (notes: String, studentId: StudentId) -> GeneratedDeck,
    judge: suspend (TurnInput) -> JudgeVerdict,
) {
    routing {
        get("/health") { call.respondText("ok") }

        rateLimit(DecksLimit) {
            post("/decks") {
                val request = call.receive<CreateDeckRequest>()
                val notes = request.notes.trim()
                if (notes.length !in NoteLimits.MIN_CHARS..NoteLimits.MAX_CHARS) {
                    call.respond(HttpStatusCode.BadRequest, ErrorBody(ErrorCodes.NOTES_LENGTH))
                    return@post
                }
                val deck = generate(notes, request.studentId)
                call.respond(
                    CreateDeckResponse(
                        language = deck.language,
                        topicTitle = deck.topicTitle,
                        misconceptions = deck.misconceptions.mapIndexed { i, m ->
                            MisconceptionDto(id = "m${i + 1}", statement = m.statement, refutation = m.refutation)
                        },
                    ),
                )
            }
        }

        rateLimit(TurnsLimit) {
            post("/lessons/turn") {
                val request = call.receive<TurnRequest>()
                val explanation = request.explanation.trim()
                if (explanation.isEmpty() || explanation.length > TurnLimits.MAX_EXPLANATION_CHARS) {
                    call.respond(HttpStatusCode.BadRequest, ErrorBody(ErrorCodes.EXPLANATION_LENGTH))
                    return@post
                }
                if (request.statement.isBlank() || request.refutation.isBlank() ||
                    request.statement.length > MAX_ITEM_CHARS || request.refutation.length > MAX_ITEM_CHARS ||
                    request.priorExchanges.any { it.explanation.length > TurnLimits.MAX_EXPLANATION_CHARS || (it.reply?.length ?: 0) > MAX_ITEM_CHARS }
                ) {
                    call.respond(HttpStatusCode.BadRequest, ErrorBody(ErrorCodes.BAD_REQUEST))
                    return@post
                }
                val input = TurnInput(
                    studentId = request.studentId,
                    statement = request.statement,
                    refutation = request.refutation,
                    stage = request.stage,
                    language = request.language,
                    explanation = explanation,
                    priorExchanges = request.priorExchanges,
                )
                if (input.turn > TurnLimits.MAX_TURNS) {
                    call.respond(HttpStatusCode.Conflict, ErrorBody(ErrorCodes.TURN_LIMIT))
                    return@post
                }
                val verdict = judge(input)
                val reply = when {
                    !verdict.addressed && input.judgeOnly -> null // dropped whatever the model wrote
                    else -> verdict.reply.trim().takeIf { it.isNotEmpty() }
                        ?: throw AiUnavailable("empty_reply")
                }
                call.respond(TurnResponse(addressed = verdict.addressed, reply = reply))
            }
        }
    }
}
