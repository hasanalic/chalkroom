package app.chalkroom.data

import app.chalkroom.shared.AppJson
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.Stage
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnRequest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The turn-5 body the app really sends must decode on a server that treats nulls explicitly. */
class TurnRequestWireTest {
    private val serverJson = Json { ignoreUnknownKeys = true; explicitNulls = true }

    @Test
    fun `turn 5 after one last try survives the round trip`() {
        val deck = CreateDeckResponse("en", "Seasons", listOf(MisconceptionDto("m1", "Summer tilts the axis.", "The tilt causes summer.")))
        var lesson = LessonLogic.startLesson(StudentId.JUNO, deck)
        repeat(4) { i -> lesson = LessonLogic.applyMiss(lesson, "try ${i + 1}", "reply ${i + 1}", "STUCK", "ANSWER") }
        lesson = LessonLogic.chooseLastTry(lesson)
        val request = TurnRequest(
            statement = lesson.current!!.statement,
            refutation = lesson.current!!.refutation,
            studentId = StudentId.JUNO,
            stage = LessonLogic.stageFor(lesson),
            language = lesson.language,
            explanation = "The tilt comes first; summer is the result.",
            priorExchanges = lesson.exchanges,
        )

        val decoded = serverJson.decodeFromString(TurnRequest.serializer(), AppJson.encodeToString(TurnRequest.serializer(), request))

        assertEquals(4, decoded.priorExchanges.size)
        assertNull(decoded.priorExchanges.last().reply)
        assertEquals(Stage.NORMAL, decoded.stage)
    }
}
