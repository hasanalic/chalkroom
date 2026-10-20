package app.chalkroom.server

import app.chalkroom.server.ai.JudgeVerdict
import app.chalkroom.server.ai.TurnInput
import app.chalkroom.shared.AppJson
import app.chalkroom.shared.Exchange
import app.chalkroom.shared.Stage
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnRequest
import app.chalkroom.shared.TurnResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The real routes, fed bodies written by the app's own serializer. */
class TurnRouteTest {

    @Test
    fun `turn 5 after a judge-only miss is accepted`() = testApplication {
        var judged: TurnInput? = null
        application {
            serve(
                generate = { _, _ -> error("not called") },
                judge = { input -> judged = input; JudgeVerdict(addressed = false, reply = "") },
            )
        }
        // What the app holds after "One last try": three replied misses, then the 4th with no reply.
        val request = TurnRequest(
            statement = "Summer happens because Earth is closer to the Sun.",
            refutation = "Seasons come from the axial tilt, not distance.",
            studentId = StudentId.JUNO,
            stage = Stage.NORMAL,
            language = "en",
            explanation = "It's the tilt: sunlight hits your hemisphere more directly in summer.",
            priorExchanges = (1..3).map { Exchange("try $it", "reply $it") } + Exchange("try 4", reply = null),
        )
        val body = AppJson.encodeToString(TurnRequest.serializer(), request)
        assertFalse("\"reply\":null" in body, "the app leaves nulls out; this test must send that shape")

        val response = client.post("/lessons/turn") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val turn = AppJson.decodeFromString(TurnResponse.serializer(), response.bodyAsText())
        assertFalse(turn.addressed)
        assertNull(turn.reply)
        assertEquals(5, judged?.turn)
        assertNull(judged?.priorExchanges?.last()?.reply)
    }
}
