package app.chalkroom.server.evals

import app.chalkroom.server.ai.MisconceptionGenerator
import app.chalkroom.server.evals.EvalReport.mark
import app.chalkroom.server.evals.EvalSupport.oneLine
import app.chalkroom.server.evals.EvalSupport.parallelMap
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Serializable
data class FlawCase(val id: String, val notes: String)

/**
 * Same notes through Mira, Pip and Juno. A blind grader (never told the student) must classify every
 * belief as that student's flaw, Pip's beliefs must name the swapped thing, and no two students may
 * share a wrong claim.
 */
class FlawShapingEval {

    private companion object {
        const val MIN_PASS_PERCENT = 80
        /** Learner target for Mira after the false-belief rule: ≥ 7/8. */
        const val MIN_MIRA_PERCENT = 87.5
    }

    private val expected = mapOf(
        StudentId.MIRA to Flaw.OMISSION,
        StudentId.PIP to Flaw.CONFUSION,
        StudentId.JUNO to Flaw.REVERSAL,
    )

    @Test
    fun `each student's beliefs are identifiable by its flaw alone`() {
        EvalSupport.assumeKey()
        val generator = MisconceptionGenerator(EvalSupport.gemini, EvalSupport.config.gen)
        val cases = EvalSupport.loadCases<FlawCase>("flaw.json")
        val failures = mutableListOf<String>()
        val misses = mutableListOf<String>()
        var beliefCount = 0
        var beliefPass = 0
        var distinctOk = true
        val deckSizes = mutableListOf<String>()
        val perStudent = StudentId.entries.associateWith { intArrayOf(0, 0) }

        val report = buildString {
            appendLine("| Notes | Student | Belief | Blind flaw | Pass | Reason |")
            appendLine("|---|---|---|---|---|---|")
            val distinct = StringBuilder()
            cases.forEach { case ->
                val decks = runBlocking {
                    StudentId.entries.parallelMap { s ->
                        s to runCatching {
                            val deck = EvalSupport.withRetry { generator.generate(case.notes, s) }
                            val beliefs = deck.misconceptions.map { it.statement }
                            beliefs to Grader.classifyBeliefs(case.notes, beliefs)
                        }
                    }
                }
                decks.forEach { (student, result) ->
                    val (beliefs, grades) = result.getOrElse {
                        failures += "${case.id}/$student error: ${it.message}"
                        misses += "**${case.id} / $student** — deck or grading failed: ${it.message?.oneLine(120)}"
                        appendLine("| ${case.id} | $student | — | — | ✗ | error: ${it.message?.oneLine(80)} |")
                        return@forEach
                    }
                    deckSizes += "${case.id}/$student=${beliefs.size}"
                    if (beliefs.size != MisconceptionGenerator.DECK_SIZE) {
                        failures += "${case.id}/$student: deck of ${beliefs.size}"
                        misses += "**${case.id} / $student** — deck of ${beliefs.size}, expected ${MisconceptionGenerator.DECK_SIZE}"
                    }
                    if (beliefs.isEmpty()) {
                        failures += "${case.id}/$student: no misconceptions"
                        misses += "**${case.id} / $student** — empty deck"
                        appendLine("| ${case.id} | $student | (none) | — | ✗ | empty deck |")
                    }
                    beliefs.forEachIndexed { i, belief ->
                        val g = grades.beliefs.firstOrNull { it.index == i }
                        val ok = g != null && g.flaw == expected.getValue(student) && g.fromNotes &&
                            (student != StudentId.PIP || g.namesSwappedInThing)
                        beliefCount++
                        perStudent.getValue(student)[1]++
                        if (ok) perStudent.getValue(student)[0]++
                        if (ok) beliefPass++ else {
                            failures += "${case.id}/$student#$i: ${g?.flaw} ${g?.reason}"
                            misses += "**${case.id} / $student** — expected ${expected.getValue(student)}, blind grader said ${g?.flaw ?: "nothing"}. " +
                                "Example: “${belief.oneLine(200)}” Grader: ${g?.reason?.oneLine(160).orEmpty()}"
                        }
                        val flaw = g?.let { it.flaw.name + if (student == StudentId.PIP && !it.namesSwappedInThing) " (swapped-in thing not named)" else "" } ?: "missing"
                        appendLine("| ${case.id} | $student | ${belief.oneLine(110)} | $flaw | ${mark(ok)} | ${g?.reason?.oneLine(90).orEmpty()} |")
                    }
                }
                val byStudent = decks.mapNotNull { (s, r) -> r.getOrNull()?.let { s.name to it.first } }.toMap()
                if (byStudent.size == StudentId.entries.size) {
                    val dup = runBlocking { Grader.findSharedBeliefs(byStudent) }
                    val ok = dup.sharedBeliefs.isEmpty()
                    if (!ok) {
                        distinctOk = false
                        failures += "${case.id}: shared beliefs ${dup.sharedBeliefs}"
                        misses += "**${case.id}** — students share a wrong claim: ${dup.sharedBeliefs.joinToString("; ").oneLine(200)}"
                    }
                    distinct.appendLine("| ${case.id} | ${mark(ok)} | ${if (ok) "none shared" else dup.sharedBeliefs.joinToString("; ").oneLine(160)} |")
                }
            }
            appendLine()
            appendLine("**Distinct across students** (no shared wrong claim):")
            appendLine()
            appendLine("| Notes | Pass | Shared beliefs |")
            appendLine("|---|---|---|")
            append(distinct)
        }
        val rateOk = beliefCount > 0 && beliefPass * 100 >= beliefCount * MIN_PASS_PERCENT
        val (miraPass, miraCount) = perStudent.getValue(StudentId.MIRA).let { it[0] to it[1] }
        val miraOk = miraCount > 0 && miraPass * 100.0 >= miraCount * MIN_MIRA_PERCENT
        val sizesOk = deckSizes.size == cases.size * StudentId.entries.size &&
            deckSizes.all { it.endsWith("=${MisconceptionGenerator.DECK_SIZE}") }
        val summary = """
            Thresholds: ≥ $MIN_PASS_PERCENT% of beliefs classified as their student's flaw (majority of ${Grader.VOTES} blind grader votes); Mira ≥ 7/8 ($MIN_MIRA_PERCENT%); every deck has ${MisconceptionGenerator.DECK_SIZE} beliefs; no wrong claim shared across students.

            - Beliefs: $beliefPass/$beliefCount = ${EvalReport.pct(beliefPass, beliefCount)} ${EvalReport.mark(rateOk)}
            - Per student: ${perStudent.entries.joinToString(", ") { (s, c) -> "${s.name} ${c[0]}/${c[1]}" }}; Mira ${EvalReport.mark(miraOk)}
            - Deck sizes: ${deckSizes.joinToString(", ")} ${EvalReport.mark(sizesOk)}
            - Distinct across students: ${EvalReport.mark(distinctOk)}
        """.trimIndent()
        EvalReport.section("3. Flaw shaping", summary + "\n\n" + report + EvalReport.knownLimitations(misses))
        assertTrue(rateOk && miraOk && sizesOk && distinctOk, "Flaw shaping below threshold (${EvalReport.pct(beliefPass, beliefCount)}, distinct=$distinctOk):\n" + failures.joinToString("\n"))
    }
}
