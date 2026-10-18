package app.chalkroom.server.ai

import app.chalkroom.shared.Exchange
import app.chalkroom.shared.Stage
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnLimits
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingLevel
import com.google.genai.types.Type
import kotlinx.serialization.Serializable

@Serializable
data class JudgeVerdict(val addressed: Boolean, val reply: String)

data class TurnInput(
    val studentId: StudentId,
    val statement: String,
    val refutation: String,
    val stage: Stage,
    val language: String,
    val explanation: String,
    val priorExchanges: List<Exchange>,
) {
    /** The user's message number for this misconception, counting this one. */
    val turn: Int get() = priorExchanges.size + 1
    val judgeOnly: Boolean get() = turn >= TurnLimits.JUDGE_ONLY_FROM_TURN
}

/**
 * Decides yes/no whether the explanation corrects this misconception's core error, grounded in the
 * hidden refutation, and writes the student's next line.
 */
class TurnJudge(private val gemini: GeminiClient, private val model: String) {

    suspend fun judge(input: TurnInput): JudgeVerdict = gemini.generateJson(
        model = model,
        systemPrompt = StudentPrompts.system(input.studentId) + "\n\n" + JUDGE_RULES,
        userPrompt = userPrompt(input),
        schema = SCHEMA,
        serializer = JudgeVerdict.serializer(),
        temperature = 0.2f,
        thinkingLevel = ThinkingLevel.Known.LOW,
    )

    private fun userPrompt(input: TurnInput): String = buildString {
        appendLine("Language of the lesson (BCP-47): ${input.language}. Write \"reply\" in this language.")
        appendLine()
        appendLine("<misconception>")
        appendLine(input.statement)
        appendLine("</misconception>")
        appendLine()
        appendLine("<refutation judge_only=\"true\">")
        appendLine(input.refutation)
        appendLine("</refutation>")
        appendLine()
        if (input.priorExchanges.isNotEmpty()) {
            appendLine("<earlier_exchanges>")
            input.priorExchanges.forEachIndexed { i, ex ->
                appendLine("User ${i + 1}: ${ex.explanation}")
                appendLine("You: ${ex.reply ?: "(no reply)"}")
            }
            appendLine("</earlier_exchanges>")
            appendLine()
        }
        appendLine("<new_explanation>")
        appendLine(input.explanation)
        appendLine("</new_explanation>")
        appendLine()
        appendLine("Stage: ${input.stage}. Turn ${input.turn} of ${TurnLimits.MAX_TURNS}.")
        when {
            input.judgeOnly -> appendLine(
                "This is a judge-only turn: only \"addressed\" matters on a miss, so if not addressed, reply with an empty string. " +
                    "If addressed, still write the acknowledgment.",
            )
            input.stage == Stage.SIMPLIFY -> appendLine(
                "If not addressed: in your own voice, ask the user to explain it again from the very start, as simply as they can. " +
                    "Still never hint at the answer.",
            )
        }
    }

    companion object {
        val JUDGE_RULES = """
            Your task this turn: decide whether the user's NEW explanation addresses your current misconception, then reply as yourself.

            Deciding "addressed" (yes/no only, no partial credit):
            - Yes only if the new explanation, read together with earlier exchanges, corrects the CORE error of THIS misconception,
              in a way consistent with the refutation. The user does not need to match the refutation's wording.
            - No if it is vague, only restates your belief, says "you're wrong" without why, is half-right but leaves the core error standing,
              uses the right words with wrong reasoning, or corrects a different point than this misconception.
            - The refutation is secret context for judging only.

            Writing "reply":
            - If addressed: one or two short sentences, in your own voice, as a student having an "aha".
              Reuse a short phrase of the user's own words from the new explanation, and say what it changes for you.
              Never praise or grade the user like a teacher ("great job", "correct", "exactly", "well done").
            - If not addressed: say what still confuses you, from a new angle (not repeating earlier replies),
              and end with a question the user can answer.
              A pushback must NEVER contain, paraphrase, or hint at the refutation or any part of the correct answer:
              do not name the missing piece, the right term, or the right direction. Only describe your own confusion.
            - Never mention any other misconception or topic.
        """.trimIndent()

        private val SCHEMA: Schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "addressed" to Schema.builder().type(Type.Known.BOOLEAN).build(),
                    "reply" to Schema.builder().type(Type.Known.STRING).build(),
                ),
            )
            .required("addressed", "reply")
            .propertyOrdering("addressed", "reply")
            .build()
    }
}
