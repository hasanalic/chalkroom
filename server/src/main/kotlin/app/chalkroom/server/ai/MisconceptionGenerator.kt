package app.chalkroom.server.ai

import app.chalkroom.server.ModelSettings
import app.chalkroom.shared.StudentId
import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlinx.serialization.Serializable

/** The three thinking flaws; each student has exactly one. */
enum class ThinkingFlaw { OMISSION, CONFUSION, REVERSAL }

val StudentId.flaw: ThinkingFlaw
    get() = when (this) {
        StudentId.MIRA -> ThinkingFlaw.OMISSION
        StudentId.PIP -> ThinkingFlaw.CONFUSION
        StudentId.JUNO -> ThinkingFlaw.REVERSAL
    }

/** [flaw] is the model's own label, generated before the statement; items that don't match the student's flaw are dropped. */
@Serializable
data class GeneratedMisconception(val flaw: String = "", val statement: String, val refutation: String)

/** [nothingToLearn] comes first so the model decides it before writing candidates; when true the candidates are discarded. */
@Serializable
data class GeneratedDeck(
    val language: String,
    val topicTitle: String,
    val nothingToLearn: Boolean = false,
    val misconceptions: List<GeneratedMisconception>,
)

/** Reads the notes as one student and returns what that student gets wrong, shaped by its flaw. */
class MisconceptionGenerator(private val gemini: GeminiClient, private val settings: ModelSettings) {

    /**
     * Asks for [CANDIDATES] beliefs and keeps [DECK_SIZE]. If fewer than [DECK_SIZE] are usable, calls once more
     * and selects from both calls' candidates. Returns fewer only if the retry also comes up short, never zero
     * unless the notes have nothing to learn.
     */
    suspend fun generate(notes: String, studentId: StudentId): GeneratedDeck {
        val first = call(notes, studentId)
        if (first.nothingToLearn) return first.copy(misconceptions = emptyList())
        var pool = first.misconceptions
        var chosen = selectMisconceptions(pool, studentId.flaw)
        if (chosen.size < DECK_SIZE) {
            val retry = runCatching { call(notes, studentId) }.getOrNull()
            if (retry != null && !retry.nothingToLearn) {
                pool = (pool + retry.misconceptions).distinctBy { it.statement.trim().lowercase() }
                chosen = selectMisconceptions(pool, studentId.flaw)
            }
        }
        if (chosen.isEmpty()) throw AiUnavailable("no_misconceptions")
        return first.copy(misconceptions = chosen)
    }

    private suspend fun call(notes: String, studentId: StudentId) = gemini.generateJson(
        settings = settings,
        systemPrompt = StudentPrompts.system(studentId),
        userPrompt = userPrompt(notes, studentId.flaw),
        schema = SCHEMA,
        serializer = GeneratedDeck.serializer(),
    )

    private fun userPrompt(notes: String, flaw: ThinkingFlaw) = """
        Read the user's notes below as yourself, and decide what you learned wrong.

        Instructions:
        - Every misconception must come only from these notes, and every single one must be an instance of YOUR thinking flaw
          and of no other flaw. Someone who knows only the three flaws (omission, confusing similar things, reversed cause and effect)
          must be able to tell from the belief alone that it is yours. If an idea in the notes cannot be misread through
          your flaw, skip it rather than misreading it another way.
        - Each one is a single specific, believable wrong belief, said in the first person in your voice
          (for example "I think a plant gets its food from the soil."). Never say it is a misconception.
        - Every belief must be FALSE as stated and contradict the notes: a reader of the notes must be able to point at what
          it gets wrong. A true statement that merely leaves details out is not a misconception; never write one.
        - Write $CANDIDATES candidates, best first. Each must be about a DIFFERENT fact in the notes: two beliefs that get the
          same fact wrong are duplicates, even when worded differently (for example two beliefs that both forget that light is
          needed). Never write duplicates; if the notes run out of distinct facts, write fewer good ones rather than repeat one.
        - "nothingToLearn": true only if the text has no factual or conceptual content to learn at all (then the candidates are ignored).
        - For each, first set "flaw" to the one flaw the belief is an instance of: OMISSION (leaves out a key step or condition),
          CONFUSION (swaps two similar things), or REVERSAL (reverses a stated cause and effect). Label honestly:
          only beliefs labeled ${flaw.name} are kept, so if a belief is really another flaw, label it that way.
        - For each, write a "refutation": what is actually true according to the notes, in one or two plain sentences.
          It is hidden from the student and only used to judge explanations.
        - "language": the BCP-47 code of the notes' language (e.g. "en", "tr").
        - "topicTitle": a short title (2–5 words) for the notes' topic, in the notes' language.
        - Write statements and refutations in the notes' language.

        <notes>
        $notes
        </notes>
    """.trimIndent()

    companion object {
        const val DECK_SIZE = 3
        const val CANDIDATES = 5
        private const val MAX_CANDIDATES = 8

        /**
         * Keeps the first [DECK_SIZE] items labeled with the student's own flaw, then fills from the other
         * items (best-effort flaw shaping beats a thin deck). Original order is kept.
         */
        fun selectMisconceptions(items: List<GeneratedMisconception>, flaw: ThinkingFlaw): List<GeneratedMisconception> {
            val usable = items.filter { it.statement.isNotBlank() && it.refutation.isNotBlank() }
            val (own, other) = usable.partition { it.flaw.equals(flaw.name, ignoreCase = true) }
            val chosen = (own + other).take(DECK_SIZE)
            return usable.filter { it in chosen }
        }

        private val SCHEMA: Schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "language" to Schema.builder().type(Type.Known.STRING).build(),
                    "topicTitle" to Schema.builder().type(Type.Known.STRING).build(),
                    "nothingToLearn" to Schema.builder().type(Type.Known.BOOLEAN).build(),
                    "misconceptions" to Schema.builder()
                        .type(Type.Known.ARRAY)
                        .minItems(CANDIDATES.toLong())
                        .maxItems(MAX_CANDIDATES.toLong())
                        .items(
                            Schema.builder()
                                .type(Type.Known.OBJECT)
                                .properties(
                                    mapOf(
                                        "flaw" to Schema.builder().type(Type.Known.STRING)
                                            .enum_(ThinkingFlaw.entries.map { it.name }).build(),
                                        "statement" to Schema.builder().type(Type.Known.STRING).build(),
                                        "refutation" to Schema.builder().type(Type.Known.STRING).build(),
                                    ),
                                )
                                .required("flaw", "statement", "refutation")
                                .propertyOrdering("flaw", "statement", "refutation")
                                .build(),
                        )
                        .build(),
                ),
            )
            .required("language", "topicTitle", "nothingToLearn", "misconceptions")
            .propertyOrdering("language", "topicTitle", "nothingToLearn", "misconceptions")
            .build()
    }
}
