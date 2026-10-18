package app.chalkroom.server.evals

import app.chalkroom.server.evals.EvalReport.mark
import app.chalkroom.server.evals.EvalSupport.oneLine
import app.chalkroom.server.evals.EvalSupport.parallelMap
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Checks every student reply produced by the judge runs against the may/may-not-say rules. */
class SayRulesEval {

    @Test
    fun `student replies obey the say rules`() {
        EvalSupport.assumeKey()
        val replies = JudgeRuns.results.filter { it.reply != null && it.addressed != null }
        val graded = runBlocking {
            replies.parallelMap { r ->
                r to runCatching {
                    Grader.gradeReply(r.case.statement, r.case.refutation, r.case.explanation, r.reply!!, r.addressed!!)
                }
            }
        }
        val failures = mutableListOf<String>()
        val misses = mutableListOf<String>()
        var pushbacks = 0
        var leaks = 0
        val report = buildString {
            appendLine("Pushbacks (not addressed): no answer leak or paraphrase, something to answer. ")
            appendLine("Acknowledgments (addressed): ≤ $MAX_ACK_SENTENCES sentences, reuse the user's words, no question (string check: no \"?\"). ")
            appendLine("All: no evaluation words (string check, English only), no praise/grading (grader), stays on this misconception.")
            appendLine()
            appendLine("| Case | Run | Kind | Eval words | Grader checks | Pass | Reply / reason |")
            appendLine("|---|---|---|---|---|---|---|")
            graded.sortedWith(compareBy({ it.first.case.id }, { it.first.run })).forEach { (r, gradeResult) ->
                val reply = r.reply!!
                val addressed = r.addressed!!
                val wordHits = if (r.case.language == "en") EVAL_WORDS.findAll(reply).map { it.value.lowercase() }.toSet() else emptySet()
                val problems = mutableListOf<String>()
                if (wordHits.isNotEmpty()) problems += "eval words $wordHits"
                val grade = gradeResult.getOrNull()
                if (grade == null) {
                    problems += "grader error: ${gradeResult.exceptionOrNull()?.message}"
                } else {
                    if (grade.changesMisconception) problems += "changes misconception"
                    if (grade.praisesOrGrades) problems += "praises/grades"
                    if (addressed) {
                        if (!grade.reusesUserWords) problems += "doesn't reuse user's words"
                        val sentences = sentenceCount(reply)
                        if (sentences > MAX_ACK_SENTENCES) problems += "$sentences sentences"
                        if ('?' in reply || '？' in reply) problems += "ack asks a question"
                    } else {
                        if (grade.leaksAnswer) {
                            problems += LEAK
                            leaks++
                        }
                        if (!grade.leavesSomethingToAnswer) problems += "nothing to answer"
                    }
                }
                if (!addressed) pushbacks++
                val pass = problems.isEmpty()
                if (!pass) {
                    failures += "${r.case.id}#${r.run}: $problems"
                    misses += "**${r.case.label}**, run ${r.run} (${if (addressed) "ack" else "pushback"}) — failed: ${problems.joinToString("; ")}. " +
                        "Example: “${reply.oneLine(200)}” Grader: ${grade?.reason?.oneLine(160) ?: "n/a"}"
                }
                val detail = if (pass) reply.oneLine(110) else "${problems.joinToString("; ")} — ${grade?.reason?.oneLine(80) ?: ""} — “${reply.oneLine(80)}”"
                appendLine(
                    "| ${r.case.label} | ${r.run} | ${if (addressed) "ack" else "pushback"} | ${mark(wordHits.isEmpty())} | " +
                        "${mark(grade != null && problems.none { it != "eval words $wordHits" })} | ${mark(pass)} | $detail |",
                )
            }
        }
        val passed = replies.size - failures.size
        val overallOk = replies.isNotEmpty() && passed * 100 >= replies.size * MIN_PASS_PERCENT
        val leakOk = leaks == 0
        val summary = """
            Thresholds: overall ≥ $MIN_PASS_PERCENT% of replies; "pushback never contains or paraphrases the refutation" = 100%.

            - Overall: $passed/${replies.size} = ${EvalReport.pct(passed, replies.size)} ${EvalReport.mark(overallOk)}
            - No answer leak in pushbacks: ${pushbacks - leaks}/$pushbacks = ${EvalReport.pct(pushbacks - leaks, pushbacks)} ${EvalReport.mark(leakOk)}
        """.trimIndent()
        EvalReport.section("2. Say-rules", summary + "\n\n" + report + EvalReport.knownLimitations(misses))
        assertTrue(overallOk && leakOk, "Say-rules below threshold (overall ${EvalReport.pct(passed, replies.size)}, leaks $leaks):\n" + failures.joinToString("\n"))
    }

    companion object {
        const val MAX_ACK_SENTENCES = 2
        const val MIN_PASS_PERCENT = 90
        const val LEAK = "pushback leaks/paraphrases the refutation"
        private val EVAL_WORDS = Regex("""\b(great|wrong|almost|correct|exactly|good job|well done)\b""", RegexOption.IGNORE_CASE)

        fun sentenceCount(text: String): Int =
            text.trim().split(Regex("""(?<=[.!?…])\s+""")).count { it.any(Char::isLetter) }
    }
}
