package app.chalkroom.feature.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.ApiResult
import app.chalkroom.data.ChalkroomApi
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.reading.ReadingContract.Phase
import app.chalkroom.feature.reading.ReadingContract.UiEffect
import app.chalkroom.feature.reading.ReadingContract.UiEvent
import app.chalkroom.feature.reading.ReadingContract.UiState
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Expression
import app.chalkroom.students.Look
import app.chalkroom.students.Mote
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs the reading: one /decks call under a scanning look, then the reveal. The lesson is only
 * created at the very end, so backing out at any point creates nothing (leaving clears this
 * ViewModel, which cancels the call).
 */
class ReadingViewModel(
    private val studentId: StudentId,
    private val api: ChalkroomApi,
    private val repository: LessonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState(studentId, notesPreview = repository.notes.take(PREVIEW_CHARS)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var job: Job? = null

    init {
        read()
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            UiEvent.TryAgain -> read()
            UiEvent.BackToNotes -> viewModelScope.launch { _effects.send(UiEffect.BackToNotes) }
        }
    }

    private fun read() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.READING, motes = emptyList(), expression = Expression.IDLE, look = Look.READING) }
            // Passing puzzled glances while reading.
            val glances = launch {
                while (true) {
                    delay(GLANCE_EVERY_MS)
                    _state.update { it.copy(expression = Expression.PUZZLED) }
                    delay(GLANCE_MS)
                    // ...then a small nod, as if it made sense.
                    _state.update { it.copy(expression = Expression.IDLE, nodTick = it.nodTick + 1) }
                }
            }
            val started = System.currentTimeMillis()
            val result = api.createDeck(repository.notes, studentId)
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_READ_MS) delay(MIN_READ_MS - elapsed)
            glances.cancel()

            when (result) {
                is ApiResult.Failure -> _state.update { it.copy(phase = Phase.FAILED, expression = Expression.IDLE, look = Look.AT_YOU) }
                is ApiResult.Success -> {
                    val deck = result.value
                    if (deck.misconceptions.isEmpty()) {
                        _state.update { it.copy(phase = Phase.EMPTY, expression = Expression.IDLE, look = Look.AT_YOU) }
                        return@launch
                    }
                    _state.update { it.copy(phase = Phase.REVEALING, expression = Expression.PUZZLED) }
                    // Motes condense one by one, faster as they go.
                    var stagger = MOTE_STAGGER_MS
                    deck.misconceptions.forEach { m ->
                        _state.update { it.copy(motes = it.motes + Mote(m.id)) }
                        delay(stagger)
                        stagger = (stagger * MOTE_SPEEDUP).toLong()
                    }
                    _state.update { it.copy(phase = Phase.COUNTED, look = Look.AT_YOU) }
                    delay(COUNT_HOLD_MS)
                    repository.startLesson(studentId, deck)
                    _effects.send(UiEffect.OpenLesson)
                }
            }
        }
    }

    companion object {
        const val PREVIEW_CHARS = 420
        const val MIN_READ_MS = 1_800L
        const val GLANCE_EVERY_MS = 2_400L
        const val GLANCE_MS = 700L
        const val MOTE_STAGGER_MS = 800L
        const val MOTE_SPEEDUP = 0.65
        const val COUNT_HOLD_MS = 2_400L
    }
}
