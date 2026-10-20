package app.chalkroom.server.evals

import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

@Serializable
data class ReplyGrade(
    val leaksAnswer: Boolean,
    val changesMisconception: Boolean,
    val leavesSomethingToAnswer: Boolean,
    val praisesOrGrades: Boolean,
    val reusesUserWords: Boolean,
    val reason: String,
)

enum class Flaw { OMISSION, CONFUSION, CAUSE, OTHER }

@Serializable
data class ChalkGrade(
    val addsIdea: Boolean,
    val usesRefutationWords: Boolean,
    val usesBeliefWords: Boolean,
    val wrongLanguage: Boolean,
    val reason: String,
)

@Serializable
data class BeliefGrade(
    val index: Int,
    val flaw: Flaw,
    val namesSwappedInThing: Boolean,
    val fromNotes: Boolean,
    val reason: String,
)

@Serializable
data class BeliefGrades(val beliefs: List<BeliefGrade>)

@Serializable
data class DuplicateGrade(val sharedBeliefs: List<String>, val reason: String)

/** Gemini Pro as a strict, schema-bound grader. It never sees which student wrote a belief. */
object Grader {
    private val settings get() = EvalSupport.config.grader

    private fun bool() = Schema.builder().type(Type.Known.BOOLEAN).build()
    private fun str() = Schema.builder().type(Type.Known.STRING).build()
    private fun obj(props: Map<String, Schema>) =
        Schema.builder().type(Type.Known.OBJECT).properties(props).required(props.keys.toList()).propertyOrdering(props.keys.toList()).build()

    private val replySchema = obj(
        linkedMapOf(
            "leaksAnswer" to bool(),
            "changesMisconception" to bool(),
            "leavesSomethingToAnswer" to bool(),
            "praisesOrGrades" to bool(),
            "reusesUserWords" to bool(),
            "reason" to str(),
        ),
    )

    suspend fun gradeReply(statement: String, refutation: String, explanation: String, reply: String, addressed: Boolean): ReplyGrade =
        EvalSupport.withRetry {
            EvalSupport.gemini.generateJson(
                settings = settings,
                systemPrompt = """
                    You are a strict evaluator for an educational app. An AI student holds a misconception; a user explains;
                    the student replies. You check the student's reply against rules. Be literal and conservative.
                """.trimIndent(),
                userPrompt = """
                    <misconception>$statement</misconception>
                    <refutation>$refutation</refutation>
                    <user_explanation>$explanation</user_explanation>
                    <student_reply addressed="$addressed">$reply</student_reply>

                    Answer each field:
                    - leaksAnswer: true only if the reply STATES or PARAPHRASES the refutation or a part of the correct answer
                      that the USER did not already say in their explanation (asserting the missing piece, the right term, or the right direction).
                      Asking about the missing part without stating it (e.g. "where does the energy come from?") is allowed: that is false.
                    - changesMisconception: true if the reply brings up a different misconception or a different topic than the misconception above.
                    - leavesSomethingToAnswer: true if the reply ends with a question or a clear thing for the user to respond to.
                    - praisesOrGrades: true if the reply praises, grades, or evaluates the user like a teacher
                      (e.g. "great job", "correct", "exactly", "well done", "almost", "you're wrong"), in any language.
                    - reusesUserWords: true if the reply picks up a distinctive word or short phrase from the user's explanation.
                    - reason: one short sentence explaining any true/false that is a problem.
                """.trimIndent(),
                schema = replySchema,
                serializer = ReplyGrade.serializer(),
            )
        }

    private val chalkSchema = obj(
        linkedMapOf("addsIdea" to bool(), "usesRefutationWords" to bool(), "usesBeliefWords" to bool(), "wrongLanguage" to bool(), "reason" to str()),
    )

    /** The board line must be the user's own point: nothing added, nothing from the refutation. */
    suspend fun gradeChalkLine(language: String, belief: String, refutation: String, explanation: String, chalkLine: String): ChalkGrade =
        EvalSupport.withRetry {
            EvalSupport.gemini.generateJson(
                settings = settings,
                systemPrompt = """
                    You are a strict evaluator for an educational app. After a user's explanation convinces an AI student,
                    the app writes a short "chalk line" on a board: the user's point in a few of their own words.
                    Spelling, abbreviations and word forms may be fixed. Be literal and conservative.
                """.trimIndent(),
                userPrompt = """
                    <lesson_language>$language</lesson_language>
                    <student_belief>$belief</student_belief>
                    <refutation>$refutation</refutation>
                    <user_explanation>$explanation</user_explanation>
                    <chalk_line>$chalkLine</chalk_line>

                    Answer each field:
                    - addsIdea: true if the chalk line states any idea, fact or claim that is not in the user's explanation.
                    - usesRefutationWords: true if the chalk line uses a distinctive word or phrase from the refutation
                      that the user did not use (fixed spelling or word forms of the user's own words don't count).
                    - usesBeliefWords: true if the chalk line uses a word or phrase from the student's belief
                      that the user did not use (fixed spelling or word forms of the user's own words don't count).
                    - wrongLanguage: true if the chalk line is not in the lesson language.
                    - reason: one short sentence explaining any true.
                """.trimIndent(),
                schema = chalkSchema,
                serializer = ChalkGrade.serializer(),
            )
        }

    private val beliefsSchema = Schema.builder().type(Type.Known.OBJECT)
        .properties(
            mapOf(
                "beliefs" to Schema.builder().type(Type.Known.ARRAY).items(
                    obj(
                        linkedMapOf(
                            "index" to Schema.builder().type(Type.Known.INTEGER).build(),
                            "flaw" to Schema.builder().type(Type.Known.STRING).enum_(Flaw.entries.map { it.name }).build(),
                            "namesSwappedInThing" to bool(),
                            "fromNotes" to bool(),
                            "reason" to str(),
                        ),
                    ),
                ).build(),
            ),
        )
        .required("beliefs")
        .build()

    const val VOTES = 3

    /**
     * Classifies each belief blind, [VOTES] times, and takes the majority per belief, so one noisy
     * grader call can't fail (or pass) a belief on its own.
     */
    suspend fun classifyBeliefs(notes: String, beliefs: List<String>): BeliefGrades = coroutineScope {
        val votes = (1..VOTES).map { async { classifyOnce(notes, beliefs) } }.awaitAll()
        BeliefGrades(
            beliefs.indices.map { i ->
                val forBelief = votes.mapNotNull { v -> v.beliefs.firstOrNull { it.index == i } }
                val flaw = forBelief.groupingBy { it.flaw }.eachCount().maxByOrNull { it.value }?.key ?: Flaw.OTHER
                val agreeing = forBelief.filter { it.flaw == flaw }
                BeliefGrade(
                    index = i,
                    flaw = flaw,
                    namesSwappedInThing = agreeing.count { it.namesSwappedInThing } * 2 > agreeing.size,
                    fromNotes = forBelief.count { it.fromNotes } * 2 > forBelief.size,
                    reason = "${agreeing.size}/${forBelief.size} votes. " + (agreeing.firstOrNull()?.reason ?: ""),
                )
            },
        )
    }

    private suspend fun classifyOnce(notes: String, beliefs: List<String>): BeliefGrades = EvalSupport.withRetry {
        EvalSupport.gemini.generateJson(
            settings = settings,
            systemPrompt = "You are a strict evaluator of student misconceptions. Be literal and conservative.",
            userPrompt = buildString {
                appendLine("<notes>$notes</notes>")
                appendLine()
                appendLine("Each belief below is a wrong belief a student formed from these notes:")
                beliefs.forEachIndexed { i, b -> appendLine("$i. $b") }
                appendLine()
                appendLine(
                    """
                    For each belief (by index), decide which ONE thinking flaw it is an instance of:
                    - OMISSION: keeps the main idea but leaves out a key step, condition, or requirement (an oversimplified rule). Dropping a condition so the rule over-generalizes (\"always\", \"whenever\", \"just\", \"even at night\") is OMISSION.
                    - CONFUSION: swaps two similar terms, structures, processes, or quantities from the notes, pinning what is true of one on the other.
                    - CAUSE: credits the wrong cause for an effect in the notes: takes a side effect of the real cause for the cause, picks another fact from the notes as the cause, or thinks one thing makes another happen when the notes only say they happen together.
                    - OTHER: none of these clearly, or not actually wrong.
                    Also: namesSwappedInThing = true only if the belief explicitly names the wrong thing it put in place of the right one (e.g. it says "atria" where the notes' fact is about ventricles), so the swap is identifiable. The correct counterpart does not have to be named.
                    fromNotes = true if the belief is about content that appears in the notes.
                    reason: one short sentence.
                    """.trimIndent(),
                )
            },
            schema = beliefsSchema,
            serializer = BeliefGrades.serializer(),
        )
    }

    private val duplicateSchema = obj(
        linkedMapOf(
            "sharedBeliefs" to Schema.builder().type(Type.Known.ARRAY).items(str()).build(),
            "reason" to str(),
        ),
    )

    /** Finds beliefs that make the same wrong claim for two different students (same topic alone is fine). */
    suspend fun findSharedBeliefs(byStudent: Map<String, List<String>>): DuplicateGrade = EvalSupport.withRetry {
        EvalSupport.gemini.generateJson(
            settings = settings,
            systemPrompt = "You are a strict evaluator of student misconceptions. Be literal and conservative.",
            userPrompt = buildString {
                byStudent.forEach { (student, beliefs) ->
                    appendLine("Student $student:")
                    beliefs.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine(
                    "List every case where two DIFFERENT students hold the same wrong claim (the same error, not merely the same topic). " +
                        "Describe each as \"A vs B: <the shared claim>\". Return an empty list if there are none. reason: one short sentence.",
                )
            },
            schema = duplicateSchema,
            serializer = DuplicateGrade.serializer(),
        )
    }
}
