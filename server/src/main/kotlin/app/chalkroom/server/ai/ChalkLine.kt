package app.chalkroom.server.ai

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
}
