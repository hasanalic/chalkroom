package app.chalkroom.server.ai

import app.chalkroom.shared.StudentId
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingLevel
import com.google.genai.types.Type
import kotlinx.serialization.Serializable

@Serializable
data class GeneratedMisconception(val statement: String, val refutation: String)

@Serializable
data class GeneratedDeck(
    val language: String,
    val topicTitle: String,
    val misconceptions: List<GeneratedMisconception>,
)

/** Reads the notes as one student and returns what that student gets wrong, shaped by its flaw. */
class MisconceptionGenerator(private val gemini: GeminiClient, private val model: String) {

    suspend fun generate(notes: String, studentId: StudentId): GeneratedDeck {
        val deck = gemini.generateJson(
            model = model,
            systemPrompt = StudentPrompts.system(studentId),
            userPrompt = userPrompt(notes),
            schema = SCHEMA,
            serializer = GeneratedDeck.serializer(),
            thinkingLevel = ThinkingLevel.Known.LOW,
        )
        return deck.copy(
            misconceptions = deck.misconceptions
                .filter { it.statement.isNotBlank() && it.refutation.isNotBlank() }
                .take(MAX_MISCONCEPTIONS),
        )
    }

    private fun userPrompt(notes: String) = """
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
                                        "statement" to Schema.builder().type(Type.Known.STRING).build(),
                                        "refutation" to Schema.builder().type(Type.Known.STRING).build(),
                                    ),
                                )
                                .required("statement", "refutation")
                                .propertyOrdering("statement", "refutation")
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
