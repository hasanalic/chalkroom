package app.chalkroom.server.ai

import java.text.Normalizer

/** Tidies the judge's chalk line for the board; a missing or runaway line becomes no line. */
object ChalkLine {
    const val MAX_WORDS = 10

    fun clean(raw: String): String? {
        val line = raw
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', '„', '«', '»', '\'')
            .trimEnd('.', '。', '!', ';', ',', ' ')
            .trim()
        if (line.isEmpty()) return null
        if (line.split(' ').size > MAX_WORDS) return null
        return line
    }

    /** Languages whose words usually carry letters beyond a–z. */
    private val LETTERED = setOf("tr", "az", "de", "fr", "es", "pt", "pl", "cs", "sk", "ro", "hu", "sv", "nb", "no", "da", "fi", "is", "vi")

    /**
     * True when a line in a lettered language came back in plain a–z only, as if copied from a user
     * who typed without their keyboard's letters, so it is worth one respell.
     */
    fun mayHaveLostLetters(language: String, line: String): Boolean =
        language.substringBefore('-').lowercase() in LETTERED && line.isNotBlank() && line.all { it.code < 128 }

    /** The respelled line, if it only puts letters back (same words otherwise); else null. */
    fun acceptRespell(original: String, respelled: String): String? {
        val r = clean(respelled) ?: return null
        return r.takeIf { it != original && fold(it) == fold(original) }
    }

    /** Lower case, accents and language letters folded to a–z: "Işık İçin" → "isik icin". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace('ı', 'i').replace("i̇", "i"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace("ß", "ss").replace('ø', 'o').replace("æ", "ae").replace('ł', 'l').replace('đ', 'd')
}
