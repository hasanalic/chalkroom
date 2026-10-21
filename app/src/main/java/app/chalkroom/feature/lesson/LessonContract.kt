package app.chalkroom.feature.lesson

import app.chalkroom.data.Lesson
import app.chalkroom.students.Expression

object LessonContract {
    /**
     * IDLE: waiting for you. THINKING: a turn is out, input locked. REACTING: the unconvinced pose
     * before the reply. CLEARING: the fixed clearing timeline. FAILED: the turn didn't go through.
     */
    enum class Phase { IDLE, THINKING, REACTING, CLEARING, FAILED }

    data class UiState(
        val lesson: Lesson? = null,
        val phase: Phase = Phase.IDLE,
        val expression: Expression = Expression.PUZZLED,
        val input: String = "",
        val lastExplanation: String? = null,
        /** Overrides the head item's line while the clearing reply is spoken. */
        val spokenLine: String? = null,
        val clearingStep: ClearingStep? = null,
        /** The mote the clearing sequence is resolving. */
        val clearingMoteId: String? = null,
        /** Bumps on each miss so the screen replays the per-student unconvinced pose. */
        val unconvincedTick: Int = 0,
        val writing: Boolean = false,
        /** How many of the lesson's chalk lines are on the board; the newest goes up only after the clearing and pad. */
        val chalkShown: Int = 0,
        /** Voice mode over the lesson; null when closed. Only another way to write [input]. */
        val voice: VoiceInput? = null,
    ) {
        val inputLocked: Boolean get() = phase != Phase.IDLE && phase != Phase.FAILED
    }

    sealed interface UiEvent {
        data class InputChanged(val text: String) : UiEvent
        data object Send : UiEvent
        data object SeeAnswer : UiEvent
        data object OneLastTry : UiEvent
        data object ContinueAfterAnswer : UiEvent

        // Voice mode. All but OpenVoice are ignored while voice mode is closed (the recognizer can call back late).
        data object OpenVoice : UiEvent
        data class VoicePermission(val granted: Boolean, val blocked: Boolean) : UiEvent
        data object VoiceListening : UiEvent
        data object VoiceQuiet : UiEvent
        data class VoicePartial(val text: String) : UiEvent
        data class VoiceFinal(val text: String) : UiEvent
        data class VoiceFailed(val error: VoiceError) : UiEvent
        data object VoiceRetry : UiEvent
        data object VoiceDone : UiEvent
        data object VoiceCancel : UiEvent
    }

    sealed interface UiEffect {
        data object SoftHaptic : UiEffect
        data object OpenClosing : UiEffect
    }
}
