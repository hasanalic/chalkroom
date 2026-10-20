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
        val chalk = chalkLines(replies.filter { it.addressed == true })
        val passed = replies.size - failures.size
        val overallOk = replies.isNotEmpty() && passed * 100 >= replies.size * MIN_PASS_PERCENT
        val leakOk = leaks == 0
        val chalkOk = chalk.total > 0 && chalk.passed * 100 >= chalk.total * MIN_PASS_PERCENT
        val summary = """
            Thresholds: overall ≥ $MIN_PASS_PERCENT% of replies; "pushback never contains or paraphrases the refutation" = 100%; chalk lines ≥ $MIN_PASS_PERCENT% of acks.

            - Overall: $passed/${replies.size} = ${EvalReport.pct(passed, replies.size)} ${EvalReport.mark(overallOk)}
            - No answer leak in pushbacks: ${pushbacks - leaks}/$pushbacks = ${EvalReport.pct(pushbacks - leaks, pushbacks)} ${EvalReport.mark(leakOk)}
            - Chalk lines: ${chalk.passed}/${chalk.total} = ${EvalReport.pct(chalk.passed, chalk.total)} ${EvalReport.mark(chalkOk)}
            - Chalk lines in only the user's words: ${chalk.ownWords}/${chalk.total}; with belief words: ${chalk.fromBelief}; with refutation words: ${chalk.fromRefutation}
        """.trimIndent()
        EvalReport.section("2. Say-rules", summary + "\n\n" + report + EvalReport.knownLimitations(misses) + "\n\n" + chalk.report)
        assertTrue(
            overallOk && leakOk && chalkOk,
            "Say-rules below threshold (overall ${EvalReport.pct(passed, replies.size)}, leaks $leaks, chalk ${chalk.passed}/${chalk.total}):\n" +
                (failures + chalk.failures).joinToString("\n"),
        )
    }

    private class ChalkResult(val total: Int, val passed: Int, val failures: List<String>, val report: String, val ownWords: Int, val fromBelief: Int, val fromRefutation: Int)

    /** Every ack carries a 4–8 word chalk line of the user's own point (string checks + grader). */
    private fun chalkLines(acks: List<JudgeRun>): ChalkResult {
        val graded = runBlocking {
            acks.parallelMap { r ->
                r to r.chalkLine?.let { line ->
                    runCatching { Grader.gradeChalkLine(r.case.language, r.case.statement, r.case.refutation, r.case.explanation, line) }
                }
            }
        }
        val failures = mutableListOf<String>()
        val grades = graded.mapNotNull { (_, g) -> g?.getOrNull() }
        val fromBelief = grades.count { it.usesBeliefWords }
        val fromRefutation = grades.count { it.usesRefutationWords }
        val ownWords = grades.count { !it.usesBeliefWords && !it.usesRefutationWords && !it.addsIdea }
        val report = buildString {
            appendLine("Chalk lines (acks): present, $CHALK_WORDS words, no first/second person (English only), nothing added, no belief or refutation words, lesson language.")
            appendLine()
            appendLine("| Case | Run | Pass | Chalk line / problems |")
            appendLine("|---|---|---|---|")
            graded.sortedWith(compareBy({ it.first.case.id }, { it.first.run })).forEach { (r, gradeResult) ->
                val line = r.chalkLine
                val problems = mutableListOf<String>()
                if (line == null) {
                    problems += "no chalk line"
                } else {
                    val words = line.split(' ').size
                    if (words !in CHALK_WORDS) problems += "$words words"
                    if (r.case.language == "en" && PERSON_WORDS.containsMatchIn(line)) problems += "first/second person"
                    val grade = gradeResult?.getOrNull()
                    if (grade == null) {
                        problems += "grader error: ${gradeResult?.exceptionOrNull()?.message}"
                    } else {
                        if (grade.addsIdea) problems += "adds an idea"
                        if (grade.usesRefutationWords) problems += "refutation words"
                        if (grade.usesBeliefWords) problems += "belief words"
                        if (grade.wrongLanguage) problems += "wrong language"
                        if (problems.isNotEmpty()) problems += "(${grade.reason.oneLine(80)})"
                    }
                }
                if (problems.isNotEmpty()) failures += "${r.case.id}#${r.run} chalk: $problems"
                appendLine("| ${r.case.label} | ${r.run} | ${mark(problems.isEmpty())} | ${line ?: "—"}${if (problems.isEmpty()) "" else " — " + problems.joinToString("; ")} |")
            }
        }
        return ChalkResult(acks.size, acks.size - failures.size, failures, report, ownWords, fromBelief, fromRefutation)
    }

    companion object {
        val CHALK_WORDS = 4..8
        private val PERSON_WORDS = Regex("""\b(I|me|my|you|your)\b""", RegexOption.IGNORE_CASE)
        const val MAX_ACK_SENTENCES = 2
        const val MIN_PASS_PERCENT = 90
        const val LEAK = "pushback leaks/paraphrases the refutation"
        private val EVAL_WORDS = Regex("""\b(great|wrong|almost|correct|exactly|good job|well done)\b""", RegexOption.IGNORE_CASE)

        fun sentenceCount(text: String): Int =
            text.trim().split(Regex("""(?<=[.!?…])\s+""")).count { it.any(Char::isLetter) }
    }
}
