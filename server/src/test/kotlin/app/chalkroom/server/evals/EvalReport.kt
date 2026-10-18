package app.chalkroom.server.evals

import java.io.File

/**
 * Collects one Markdown section per eval and rewrites `server/build/eval-report.md` after each one,
 * so a partial run still leaves a readable report.
 */
object EvalReport {
    private val sections = linkedMapOf<String, String>()
    private val file = File("build/eval-report.md")

    @Synchronized
    fun section(title: String, body: String) {
        sections[title] = body
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("# Chalkroom eval report")
                appendLine()
                with(EvalSupport.config) {
                    listOf("gen" to gen, "turn" to turn, "grader" to grader).forEach { (role, m) ->
                        appendLine("- $role: `${m.model}` thinking=${m.thinking} maxTokens=${m.maxOutputTokens ?: "default"} temperature=${m.temperature}")
                    }
                }
                appendLine()
                sections.forEach { (t, b) ->
                    appendLine("## $t")
                    appendLine()
                    appendLine(b.trimEnd())
                    appendLine()
                }
            },
        )
        println("\n## $title\n$body")
    }

    fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    fun pct(n: Int, d: Int) = if (d == 0) "n/a" else "%.1f%%".format(100.0 * n / d)

    /** Every miss is listed with its example, so tolerated misses stay visible instead of hidden in a pass rate. */
    fun knownLimitations(misses: List<String>): String = buildString {
        appendLine()
        appendLine("### Known limitations")
        appendLine()
        if (misses.isEmpty()) appendLine("None in this run.") else misses.forEach { appendLine("- $it") }
    }
}
