package app.chalkroom.feature.lesson

/**
 * STARTING: waiting on the mic permission or the recognizer. LISTENING: the recognizer is up.
 * QUIET: it heard nothing and is listening again. ERROR: stopped, see [VoiceInput.error].
 */
enum class VoicePhase { STARTING, LISTENING, QUIET, ERROR }

enum class VoiceError(val message: String) {
    PERMISSION_DENIED("Microphone access is needed for voice input."),
    PERMISSION_BLOCKED("Microphone access is off. Turn it on in Settings to use voice input."),
    UNAVAILABLE("Voice input isn't available on this device."),
    FAILED("Couldn't hear that. Try again."),
}

/** Voice mode while it is open. Only text is kept: what the recognizer settled on and its current guess. */
data class VoiceInput(
    val phase: VoicePhase = VoicePhase.STARTING,
    /** Everything the recognizer has settled on, across all its restarts. */
    val finalized: String = "",
    /** The recognizer's current guess; each new partial replaces it. */
    val partial: String = "",
    val error: VoiceError? = null,
) {
    val transcript: String get() = listOf(finalized, partial).filter { it.isNotBlank() }.joinToString(" ").trim()
    val canFinish: Boolean get() = transcript.isNotBlank()
    val wantsMic: Boolean get() = phase != VoicePhase.ERROR
}

/** [spoken] after [existing] with exactly one space between them; a blank [spoken] changes nothing. */
fun mergeSpoken(existing: String, spoken: String): String {
    val words = spoken.trim()
    return when {
        words.isEmpty() -> existing
        existing.isBlank() -> words
        else -> existing.trimEnd() + " " + words
    }
}
