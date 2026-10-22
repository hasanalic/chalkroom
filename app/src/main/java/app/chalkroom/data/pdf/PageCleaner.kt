package app.chalkroom.data.pdf

/**
 * Turns the raw text of PDF pages into clean paragraphs, page by page. Pastes never go through here.
 * Each page that survives comes back with its paragraphs joined by one blank line.
 */
object PageCleaner {

    fun clean(pages: List<PdfPage>): List<PdfPage> {
        val readable = pages.filter { isReadable(it.text) }
        val running = runningLines(readable)
        return readable.mapNotNull { page ->
            val lines = dropRunning(page.text.lines(), running)
            val text = paragraphs(joinHyphens(lines.joinToString("\n"))).joinToString("\n\n")
            text.takeIf { it.isNotEmpty() }?.let { PdfPage(page.number, it) }
        }
    }

    /** The text that will be taught: all pages' paragraphs, one blank line apart. */
    fun join(pages: List<PdfPage>): String = pages.joinToString("\n\n") { it.text }

    // 1. Unreadable pages: too few characters (a scan) or too many broken ones (a broken font).

    internal fun isReadable(text: String): Boolean {
        var visible = 0
        var broken = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp) || cp == NBSP) continue
            visible++
            if (cp == 0xFFFD || Character.getType(cp) == Character.PRIVATE_USE.toInt() || Character.isISOControl(cp)) broken++
        }
        return visible >= MIN_PAGE_CHARS && broken * 10 <= visible
    }

    // 2. Running headers and footers: the same first or last line on most pages.

    private fun runningKey(line: String) = line.trim().replace(DIGITS, "#")

    private fun edgeLines(text: String): List<String> {
        val lines = text.lines().filter { it.isNotBlank() }
        return listOfNotNull(lines.firstOrNull(), lines.lastOrNull()).map(::runningKey).distinct()
    }

    internal fun runningLines(pages: List<PdfPage>): Set<String> {
        if (pages.size < 3) return emptySet()
        val seen = pages.flatMap { edgeLines(it.text) }.groupingBy { it }.eachCount()
        return seen.filterValues { it * 10 >= pages.size * 6 }.keys
    }

    private fun dropRunning(lines: List<String>, running: Set<String>): List<String> {
        if (running.isEmpty()) return lines
        val first = lines.indexOfFirst { it.isNotBlank() }
        val last = lines.indexOfLast { it.isNotBlank() }
        return lines.filterIndexed { i, line -> !((i == first || i == last) && runningKey(line) in running) }
    }

    // 3. Line-end hyphens.

    internal fun joinHyphens(text: String): String {
        val french = isFrench(text)
        return LINE_END_HYPHEN.replace(text) { m ->
            val left = m.groupValues[1]
            val next = m.groupValues[2]
            val nextLower = next.lowercase()
            when {
                !next.first().isLowerCase() -> "$left-$next"
                nextLower in GERMAN_SUSPENDED -> "$left- $next"
                french && keepsFrenchHyphen(left.lowercase(), nextLower) -> "$left-$next"
                else -> "$left$next"
            }
        }
    }

    private fun keepsFrenchHyphen(left: String, tail: String): Boolean =
        tail in FRENCH_TAILS ||
            (left == "est" && (tail == "ce" || tail == "à")) ||
            ((tail == "ci" || tail == "là") && left in FRENCH_DEMONSTRATIVES)

    internal fun isFrench(text: String): Boolean {
        val words = text.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        return words.count { it in FRENCH_FUNCTION_WORDS } * 10 >= words.size
    }

    // 4. Paragraphs: blank lines split, single breaks are layout.

    internal fun paragraphs(text: String): List<String> = BLANK_LINE.split(text).flatMap { block ->
        val lines = block.lines().map(::collapse).filter { it.isNotEmpty() }
        when {
            lines.isEmpty() -> emptyList()
            lines.size > 1 && isBlockHeading(lines) -> listOf(lines.first(), joinLines(lines.drop(1)))
            else -> listOf(joinLines(lines))
        }
    }.filter { it.isNotEmpty() }

    /**
     * A first line that looks like a heading, and is clearly shorter than the lines under it, so a
     * wrapped first line of prose that just happens to end without punctuation isn't taken for one.
     */
    private fun isBlockHeading(lines: List<String>): Boolean {
        val first = lines.first()
        if (!looksLikeHeading(first) || first.first().isLowerCase()) return false
        val widest = lines.drop(1).maxOf { displayWidth(it) }
        return displayWidth(first) * 10 < widest * 8
    }

    private fun joinLines(lines: List<String>): String = buildString {
        for (line in lines) {
            if (isNotEmpty() && !isCjk(last()) && !isCjk(line.first())) append(' ')
            append(line)
        }
    }

    private fun collapse(s: String) = SPACES.replace(s, " ").trim()

    private const val NBSP = 0x00A0
    private const val MIN_PAGE_CHARS = 50
    private val DIGITS = Regex("\\d+")
    private val BLANK_LINE = Regex("\\n[ \\t\\u00A0]*\\n\\s*")
    private val SPACES = Regex("[ \\t\\u00A0]+")
    private val NON_LETTERS = Regex("[^\\p{L}]+")
    private val LINE_END_HYPHEN = Regex("(\\p{L}+)[-\\u2010\\u00AD][ \\t]*\\n[ \\t]*(\\p{L}+)")
    private val GERMAN_SUSPENDED = setOf("und", "oder", "bis", "sowie")
    private val FRENCH_FUNCTION_WORDS = setOf(
        "le", "la", "les", "un", "une", "des", "du", "au", "aux", "et", "est", "dans", "pour", "qui", "que",
        "pas", "sur", "avec", "ce", "cette", "il", "elle", "c", "l", "d", "qu",
    )
    private val FRENCH_TAILS = setOf(
        "être", "même", "mêmes", "dessus", "dessous", "delà",
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "t",
    )
    private val FRENCH_DEMONSTRATIVES = setOf("celui", "celle", "ceux", "celles")
}
