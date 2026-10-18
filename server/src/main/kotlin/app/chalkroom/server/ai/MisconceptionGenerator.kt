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

@Serializable
data class GeneratedDeck(
    val language: String,
    val topicTitle: String,
    val misconceptions: List<GeneratedMisconception>,
)

/** Reads the notes as one student and returns what that student gets wrong, shaped by its flaw. */
class MisconceptionGenerator(private val gemini: GeminiClient, private val settings: ModelSettings) {

    suspend fun generate(notes: String, studentId: StudentId): GeneratedDeck {
        val deck = gemini.generateJson(
            settings = settings,
            systemPrompt = StudentPrompts.system(studentId),
            userPrompt = userPrompt(notes, studentId.flaw),
            schema = SCHEMA,
            serializer = GeneratedDeck.serializer(),
        )
        return deck.copy(misconceptions = selectMisconceptions(deck.misconceptions, studentId.flaw))
    }

    private fun userPrompt(notes: String, flaw: ThinkingFlaw) = """
        Read the user's notes below as yourself, and decide what you learned wrong.

        Instructions:
        - Every misconception must come only from these notes, and every single one must be an instance of YOUR thinking flaw
          and of no other flaw. Someone who knows only the three flaws (omission, confusing similar things, reversed cause and effect)
          must be able to tell from the belief alone that it is yours. If an idea in the notes cannot be misread through
          your flaw, skip it rather than misreading it another way.
        - Each one is a single specific, believable wrong belief, said in the first person in your voice
          (for example "I think a plant gets its food from the soil."). Never say it is a misconception.
        - Scale the count to the material: about one per distinct idea, between 1 and $MAX_MISCONCEPTIONS.
          Short notes with three ideas give about three.
        - If the text has nothing to learn (no factual or conceptual content), return an empty list.
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
        const val MAX_MISCONCEPTIONS = 8
        const val MIN_MISCONCEPTIONS = 3

        /**
         * Keeps items labeled with the student's own flaw. If that leaves fewer than [MIN_MISCONCEPTIONS],
         * tops up with the other generated items (best-effort flaw shaping beats a thin deck or a false
         * "nothing to misunderstand"). Original order is kept. Can't exceed what the model returned.
         */
        fun selectMisconceptions(items: List<GeneratedMisconception>, flaw: ThinkingFlaw): List<GeneratedMisconception> {
            val usable = items.filter { it.statement.isNotBlank() && it.refutation.isNotBlank() }
            val (own, other) = usable.partition { it.flaw.equals(flaw.name, ignoreCase = true) }
            val chosen = own.take(MAX_MISCONCEPTIONS) + other.take((MIN_MISCONCEPTIONS - own.size).coerceAtLeast(0))
            return usable.filter { it in chosen }
        }

        private val SCHEMA: Schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "language" to Schema.builder().type(Type.Known.STRING).build(),
                    "topicTitle" to Schema.builder().type(Type.Known.STRING).build(),
                    "misconceptions" to Schema.builder()
                        .type(Type.Known.ARRAY)
                        .minItems(0L)
                        .maxItems(MAX_MISCONCEPTIONS.toLong())
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
            .required("language", "topicTitle", "misconceptions")
            .propertyOrdering("language", "topicTitle", "misconceptions")
            .build()
    }
}
