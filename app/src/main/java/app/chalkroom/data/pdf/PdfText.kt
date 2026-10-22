package app.chalkroom.data.pdf

/** One page of text. [number] is the PDF's own page number; null for a paste. */
data class PdfPage(val number: Int?, val text: String)

/** Caps on what is read from a PDF on the device. */
object PdfLimits {
    const val MAX_FILE_BYTES = 50L * 1024 * 1024
    const val MAX_PAGES = 150
}

internal fun isCjk(c: Char): Boolean {
    val block = Character.UnicodeBlock.of(c) ?: return false
    return block in CJK_BLOCKS
}

private val CJK_BLOCKS = setOf(
    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
    Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
    Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
    Character.UnicodeBlock.HIRAGANA,
    Character.UnicodeBlock.KATAKANA,
    Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
    Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS,
    Character.UnicodeBlock.HANGUL_SYLLABLES,
    Character.UnicodeBlock.HANGUL_JAMO,
    Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
)

private const val HEADING_WIDTH = 80
private const val HEADING_END = ".!?:;,。！？：；、，"

/** Display width: each CJK or Hangul character counts as 2. */
internal fun displayWidth(s: String): Int = s.sumOf { if (isCjk(it)) 2 else 1 }

/** At most 80 wide, has a letter, and doesn't end like a sentence or a clause. */
internal fun looksLikeHeading(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty() || '\n' in t) return false
    return displayWidth(t) <= HEADING_WIDTH && t.any { it.isLetter() } && t.last() !in HEADING_END
}

/** More than half of the non-whitespace characters are CJK or Hangul. */
internal fun isCjkHeavy(text: String): Boolean {
    var visible = 0
    var cjk = 0
    for (c in text) {
        if (c.isWhitespace()) continue
        visible++
        if (isCjk(c)) cjk++
    }
    return visible > 0 && cjk * 2 > visible
}
