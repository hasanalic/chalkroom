package app.chalkroom.server.ai

/** Tidies the judge's chalk line for the board; a missing or runaway line becomes no line. */
object ChalkLine {
    const val MAX_WORDS = 10

    /**
     * [explanation] is the user's own text: a capitalised first word they also wrote capitalised
     * mid-sentence is taken for a name ("Newton") and keeps its capital.
     */
    fun clean(raw: String, explanation: String = ""): String? {
        val line = raw
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', '„', '«', '»', '\'')
            .trimEnd('.', '。', '!', ';', ',', ' ')
            .trim()
        if (line.isEmpty()) return null
        if (line.split(' ').size > MAX_WORDS) return null
        return startLowercase(line, explanation)
    }

    /** Every line starts lowercase ("sun is higher…"), except "I", an acronym ("DNA"), or a name the user wrote. */
    private fun startLowercase(line: String, explanation: String): String {
        val first = line.substringBefore(' ').trimEnd(',', ':', ';')
        val keep = first == "I" || first.startsWith("I'") || first.startsWith("I’") ||
            first.count { it.isUpperCase() } >= 2 ||
            (first.lowercase() !in NOT_NAMES && writtenAsName(first, explanation))
        return if (keep) line else line.replaceFirstChar { it.lowercase() }
    }

    /** Capitalised, but common nouns all the same on a board. */
    private val NOT_NAMES = setOf("earth", "sun", "moon")

    private fun writtenAsName(word: String, explanation: String): Boolean =
        word.first().isUpperCase() &&
            Regex("""(?<![.!?]\s)(?<!^)(?<=\s)${Regex.escape(word)}\b""").containsMatchIn(explanation.trim())
}
