package app.chalkroom.server

import app.chalkroom.server.ai.MisconceptionGenerator
import app.chalkroom.shared.CreateDeckRequest
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.ErrorBody
import app.chalkroom.shared.ErrorCodes
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.NoteLimits
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

fun Application.configureRoutes(generator: MisconceptionGenerator) {
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
                val deck = generator.generate(notes, request.studentId)
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
    }
}
