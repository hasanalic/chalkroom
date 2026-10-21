package app.chalkroom.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.ApiResult
import app.chalkroom.data.ChalkroomApi
import app.chalkroom.data.ItemPhase
import app.chalkroom.data.LessonLogic
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.lesson.LessonContract.Phase
import app.chalkroom.feature.lesson.LessonContract.UiEffect
import app.chalkroom.feature.lesson.LessonContract.UiEvent
import app.chalkroom.feature.lesson.LessonContract.UiState
import app.chalkroom.shared.ErrorCodes
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnLimits
import app.chalkroom.shared.TurnRequest
import app.chalkroom.students.Expression
import app.chalkroom.students.StudentCatalog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One lesson's teaching loop. Every lesson change goes through [LessonLogic] into the repository;
 * this ViewModel only adds the moment-to-moment looks (thinking, unconvinced, clearing).
 * If you leave mid-moment, the pending lesson change is still committed, so nothing is lost.
 */
class LessonViewModel(
    private val studentId: StudentId,
    private val api: ChalkroomApi,
    private val repository: LessonRepository,
) : ViewModel() {

    private val profile = StudentCatalog[studentId]

    private val _state = MutableStateFlow(
        repository.lesson(studentId).let { UiState(lesson = it, chalkShown = it?.chalkLines?.size ?: 0) },
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        repository.resume(studentId)
        // Left during the last clearing: the lesson is already done, so go straight to the closing.
        if (repository.lesson(studentId)?.let(LessonLogic::isFinished) == true) {
            viewModelScope.launch { _effects.send(UiEffect.OpenClosing) }
        }
        viewModelScope.launch {
            repository.state.collect { s -> _state.update { it.copy(lesson = s.lessons[studentId]) } }
        }
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.InputChanged ->
                if (!_state.value.inputLocked) _state.update { it.copy(input = event.text.take(TurnLimits.MAX_EXPLANATION_CHARS)) }
            UiEvent.Send -> send()
            UiEvent.SeeAnswer -> repository.update(studentId) { if (it.phase == ItemPhase.STUCK) LessonLogic.revealAnswer(it, profile.answerLine) else it }
            UiEvent.OneLastTry -> repository.update(studentId, LessonLogic::chooseLastTry)
            UiEvent.ContinueAfterAnswer -> {
                val lesson = repository.lesson(studentId) ?: return
                if (lesson.phase == ItemPhase.REVEALED && _state.value.phase != Phase.CLEARING) {
                    viewModelScope.launch { clear(sentence = null, reply = null, chalkLine = null) }
                }
            }
            UiEvent.OpenVoice ->
                if (!_state.value.inputLocked && _state.value.voice == null) _state.update { it.copy(voice = VoiceInput()) }
            is UiEvent.VoicePermission ->
                if (!event.granted) {
                    voice { it.copy(phase = VoicePhase.ERROR, error = if (event.blocked) VoiceError.PERMISSION_BLOCKED else VoiceError.PERMISSION_DENIED) }
                }
            UiEvent.VoiceListening -> voice { it.copy(phase = VoicePhase.LISTENING, error = null) }
            UiEvent.VoiceQuiet -> voice { it.copy(phase = VoicePhase.QUIET) }
            is UiEvent.VoicePartial -> voice { it.copy(partial = event.text) }
            is UiEvent.VoiceFinal -> voice { it.copy(finalized = mergeSpoken(it.finalized, event.text), partial = "") }
            is UiEvent.VoiceFailed -> voice { it.copy(phase = VoicePhase.ERROR, error = event.error) }
            UiEvent.VoiceRetry -> voice { it.copy(phase = VoicePhase.STARTING, error = null) }
            UiEvent.VoiceDone -> {
                val voice = _state.value.voice ?: return
                if (!voice.canFinish) return
                // Only fills the box: never sends, never touches the round.
                _state.update { it.copy(input = mergeSpoken(it.input, voice.transcript).take(TurnLimits.MAX_EXPLANATION_CHARS), voice = null) }
            }
            UiEvent.VoiceCancel -> _state.update { it.copy(voice = null) }
        }
    }

    /** Changes voice mode only while it is open, so late recognizer callbacks after closing are dropped. */
    private fun voice(change: (VoiceInput) -> VoiceInput) {
        _state.update { s -> s.voice?.let { s.copy(voice = change(it)) } ?: s }
    }

    private fun send() {
        val lesson = repository.lesson(studentId) ?: return
        val head = lesson.current ?: return
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.inputLocked || !LessonLogic.canSend(lesson)) return

        val previousExplanation = _state.value.lastExplanation
        _state.update { it.copy(phase = Phase.THINKING, expression = Expression.THINKING, input = "", lastExplanation = text) }
        viewModelScope.launch {
            val request = TurnRequest(
                statement = head.statement,
                refutation = head.refutation,
                studentId = studentId,
                stage = LessonLogic.stageFor(lesson),
                language = lesson.language,
                explanation = text,
                priorExchanges = lesson.exchanges,
            )
            when (val result = api.turn(request)) {
                is ApiResult.Failure ->
                    if (result.code == ErrorCodes.TURN_LIMIT) {
                        // Should never happen (the app stops at 5); not an error: show the answer.
                        repository.update(studentId) { LessonLogic.revealAnswer(it, profile.answerLine) }
                        _state.update { it.copy(phase = Phase.IDLE, expression = Expression.PUZZLED) }
                    } else {
                        // Nothing changes: your words go back in the box.
                        _state.update {
                            it.copy(phase = Phase.FAILED, expression = Expression.PUZZLED, input = text, lastExplanation = previousExplanation)
                        }
                    }
                is ApiResult.Success ->
                    if (result.value.addressed) clear(sentence = text, reply = result.value.reply, chalkLine = result.value.chalkLine)
                    else miss(text, result.value.reply)
            }
        }
    }

    private suspend fun miss(explanation: String, reply: String?) {
        _state.update { it.copy(phase = Phase.REACTING, expression = Expression.PUZZLED, unconvincedTick = it.unconvincedTick + 1) }
        commitAfter({ delay(UNCONVINCED_MS) }) {
            repository.update(studentId) { LessonLogic.applyMiss(it, explanation, reply, profile.stuckLine, profile.answerLine) }
        }
        _state.update { it.copy(phase = Phase.IDLE) }
    }

    /** [sentence] null = cleared via the answer card (no reply, not in What stuck, no chalk line). */
    private suspend fun clear(sentence: String?, reply: String?, chalkLine: String?) {
        val head = repository.lesson(studentId)?.current
        _state.update { it.copy(phase = Phase.CLEARING, spokenLine = head?.currentLine, clearingStep = ClearingStep.HOLD, clearingMoteId = head?.id) }
        commitAfter({
            ClearingChoreography.run { step ->
                _state.update { it.copy(clearingStep = step) }
                when (step) {
                    ClearingStep.HOLD -> _effects.send(UiEffect.SoftHaptic)
                    ClearingStep.EYES_WIDEN -> _state.update { it.copy(expression = Expression.GOT_IT) }
                    ClearingStep.COUNTER_DROPS -> {
                        commitClear(sentence, chalkLine)
                        _effects.send(UiEffect.SoftHaptic)
                    }
                    ClearingStep.SPEAK -> _state.update { it.copy(spokenLine = reply ?: it.spokenLine, writing = reply != null) }
                    else -> Unit
                }
            }
        }) { commitClear(sentence, chalkLine) }

        val hold = if (reply == null) REVEALED_HOLD_MS else (REPLY_HOLD_MS + REPLY_MS_PER_CHAR * reply.length).coerceAtMost(REPLY_HOLD_MAX_MS)
        val lines = repository.lesson(studentId)?.chalkLines?.size ?: 0
        if (lines > _state.value.chalkShown) {
            // The chalk line is written once the clearing and the pad's squiggles are done, and gets time to finish.
            delay(PAD_MS)
            _state.update { it.copy(chalkShown = lines) }
            delay((hold - PAD_MS).coerceAtLeast(CHALK_WRITE_MS))
        } else {
            delay(hold)
        }
        _state.update {
            it.copy(phase = Phase.IDLE, expression = Expression.PUZZLED, spokenLine = null, clearingStep = null, clearingMoteId = null, writing = false, lastExplanation = null)
        }
        if (repository.lesson(studentId)?.let(LessonLogic::isFinished) == true) _effects.send(UiEffect.OpenClosing)
    }

    private var clearCommitted = false

    private fun commitClear(sentence: String?, chalkLine: String?) {
        if (clearCommitted) return
        clearCommitted = true
        repository.update(studentId) { if (sentence != null) LessonLogic.clearCurrent(it, sentence, chalkLine) else LessonLogic.clearRevealed(it) }
    }

    /** Runs [moment]; [commit] always runs after it, even if the screen is left mid-moment. */
    private suspend fun commitAfter(moment: suspend () -> Unit, commit: () -> Unit) {
        clearCommitted = false
        try {
            moment()
        } finally {
            withContext(NonCancellable) { commit() }
        }
    }

    /** Leaving the lesson pauses it. A finished lesson was already removed by the closing, so this is a no-op then. */
    override fun onCleared() {
        repository.pause(studentId)
    }

    companion object {
        const val UNCONVINCED_MS = 550L
        const val REVEALED_HOLD_MS = 900L
        const val REPLY_HOLD_MS = 1_800L
        const val REPLY_MS_PER_CHAR = 35L
        const val REPLY_HOLD_MAX_MS = 5_000L
        /** The note pad's squiggles (NotePad) after SPEAK. */
        const val PAD_MS = 1_100L
        /** Longest chalk line (writeMs) plus its underline. */
        const val CHALK_WRITE_MS = 1_900L
    }
}
