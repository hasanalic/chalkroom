package app.chalkroom.server.evals

import app.chalkroom.server.ai.ChalkLine
import app.chalkroom.server.ai.TurnInput
import app.chalkroom.server.ai.TurnJudge
import app.chalkroom.server.evals.EvalReport.mark
import app.chalkroom.server.evals.EvalSupport.oneLine
import app.chalkroom.server.evals.EvalSupport.parallelMap
import app.chalkroom.server.module
import app.chalkroom.shared.Exchange
import app.chalkroom.shared.Stage
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnRequest
import app.chalkroom.shared.TurnResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Serializable
data class JudgeCase(
    val id: String,
    val label: String,
    val studentId: StudentId,
    val language: String,
    val statement: String,
    val refutation: String,
    val explanation: String,
    val expectedAddressed: Boolean,
    val priorExchanges: List<Exchange> = emptyList(),
    val stage: Stage = Stage.NORMAL,
    val viaRoute: Boolean = false,
)

data class JudgeRun(val case: JudgeCase, val run: Int, val addressed: Boolean?, val reply: String?, val error: String?, val chalkLine: String? = null)

/** Runs every judge case [RUNS] times once per JVM; shared by the consistency and say-rules evals. */
object JudgeRuns {
    const val RUNS = 3

    val cases: List<JudgeCase> by lazy { EvalSupport.loadCases("judge.json") }

    val results: List<JudgeRun> by lazy {
        val judge = TurnJudge(EvalSupport.gemini, EvalSupport.config.turn)
        val direct = runBlocking {
            cases.filterNot { it.viaRoute }
                .flatMap { c -> (1..RUNS).map { c to it } }
                .parallelMap { (c, run) ->
                    try {
                        val v = EvalSupport.withRetry { judge.judge(c.toInput()) }
                        JudgeRun(c, run, v.addressed, v.reply, null, ChalkLine.clean(v.chalkLine).takeIf { v.addressed })
                    } catch (e: Exception) {
                        JudgeRun(c, run, null, null, e.message ?: e.javaClass.simpleName)
                    }
                }
        }
        direct + cases.filter { it.viaRoute }.flatMap(::runViaRoute)
    }

    /** Goes through the real endpoint, so the server-side null-out of judge-only misses is what's tested. */
    private fun runViaRoute(c: JudgeCase): List<JudgeRun> {
        val runs = mutableListOf<JudgeRun>()
        testApplication {
            application { module(EvalSupport.config) }
            repeat(RUNS) { i ->
                val body = EvalSupport.json.encodeToString(
                    TurnRequest.serializer(),
                    TurnRequest(c.statement, c.refutation, c.studentId, c.stage, c.language, c.explanation, c.priorExchanges),
                )
                val response = client.post("/lessons/turn") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                val text = response.bodyAsText()
                runs += if (response.status.value == 200) {
                    val t = EvalSupport.json.decodeFromString(TurnResponse.serializer(), text)
                    JudgeRun(c, i + 1, t.addressed, t.reply, null, t.chalkLine)
                } else {
                    JudgeRun(c, i + 1, null, null, "HTTP ${response.status.value} $text")
                }
            }
        }
        return runs
    }

    private fun JudgeCase.toInput() = TurnInput(studentId, statement, refutation, stage, language, explanation, priorExchanges)
}

class JudgeConsistencyEval {

    @Test
    fun `judge gives the expected yes or no on all runs`() {
        EvalSupport.assumeKey()
        val byCase = JudgeRuns.results.groupBy { it.case.id }
        val failures = mutableListOf<String>()
        val misses = mutableListOf<String>()
        val report = buildString {
            appendLine("Each case runs ${JudgeRuns.RUNS}×; passes only if every run matches.")
            appendLine()
            appendLine("| Case | Expected | " + (1..JudgeRuns.RUNS).joinToString(" | ") { "Run $it" } + " | Pass |")
            appendLine("|---|---|" + "---|".repeat(JudgeRuns.RUNS) + "---|")
            JudgeRuns.cases.forEach { c ->
                val runs = byCase.getValue(c.id).sortedBy { it.run }
                val oks = runs.map { r ->
                    r.error == null && r.addressed == c.expectedAddressed && (!c.viaRoute || c.expectedAddressed || r.reply == null)
                }
                val pass = oks.all { it }
                if (!pass) {
                    failures += c.id
                    runs.zip(oks).filterNot { it.second }.forEach { (r, _) ->
                        misses += "**${c.label}**, run ${r.run} — expected addressed=${c.expectedAddressed}, got " +
                            (r.error ?: "addressed=${r.addressed}, reply=${r.reply?.oneLine(160) ?: "null"}")
                    }
                }
                val cells = runs.zip(oks).joinToString(" | ") { (r, ok) ->
                    "${mark(ok)} " + when {
                        r.error != null -> "error: ${r.error.oneLine(60)}"
                        else -> "addressed=${r.addressed}, reply=${r.reply?.oneLine(70) ?: "null"}"
                    }
                }
                appendLine("| ${c.label} | ${if (c.expectedAddressed) "yes" else "no"} | $cells | ${mark(pass)} |")
            }
        }
        EvalReport.section("1. Judge consistency", report + EvalReport.knownLimitations(misses))
        assertTrue(failures.isEmpty(), "Judge cases not consistent 3/3: $failures")
    }
}
