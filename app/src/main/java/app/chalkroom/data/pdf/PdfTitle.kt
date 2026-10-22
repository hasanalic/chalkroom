package app.chalkroom.data.pdf

import java.text.NumberFormat
import java.util.Locale

/** File names and parts as people read them. */
object PdfTitle {

    /** `cell_biology_week_4.pdf` → "Cell Biology — Week 4"; nothing left → "Untitled notes". */
    fun fromFileName(name: String): String {
        val base = name.trim().let { n -> n.lastIndexOf('.').takeIf { it >= 0 }?.let { n.substring(0, it) } ?: n }
        val words = base.split(SEPARATORS).filter { it.isNotEmpty() }.map { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        if (words.isEmpty()) return UNTITLED
        val n = words.size
        val trailing = n >= 3 && words[n - 1].all { it.isDigit() } && words[n - 2].any { it.isLetter() }
        return if (trailing) {
            words.dropLast(2).joinToString(" ") + " — " + words[n - 2] + " " + words[n - 1]
        } else {
            words.joinToString(" ")
        }
    }

    const val UNTITLED = "Untitled notes"

    /** "Part 2 · p. 3–6", or "p. 3" when it starts and ends on the same page. */
    fun partLabel(part: Part): String {
        val pages = when {
            part.firstPage == null || part.lastPage == null -> null
            part.firstPage == part.lastPage -> "p. ${part.firstPage}"
            else -> "p. ${part.firstPage}–${part.lastPage}"
        }
        return listOfNotNull("Part ${part.index}", pages).joinToString(" · ")
    }

    /** "1,240 words", or characters for CJK-heavy text. */
    fun length(text: String): String {
        val format = NumberFormat.getIntegerInstance(Locale.US)
        return if (isCjkHeavy(text)) {
            val chars = text.count { !it.isWhitespace() }
            "${format.format(chars)} ${if (chars == 1) "character" else "characters"}"
        } else {
            val words = text.split(WHITESPACE).count { it.isNotEmpty() }
            "${format.format(words)} ${if (words == 1) "word" else "words"}"
        }
    }

    private val SEPARATORS = Regex("[_\\-.\\s]+")
    private val WHITESPACE = Regex("\\s+")
}
