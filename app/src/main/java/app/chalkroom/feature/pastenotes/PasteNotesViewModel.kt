package app.chalkroom.feature.pastenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chalkroom.data.LessonRepository
import app.chalkroom.data.pdf.PageCleaner
import app.chalkroom.data.pdf.PartCutter
import app.chalkroom.data.pdf.PartsOffer
import app.chalkroom.data.pdf.PdfLimits
import app.chalkroom.data.pdf.PdfOpenResult
import app.chalkroom.data.pdf.PdfParts
import app.chalkroom.data.pdf.PdfReader
import app.chalkroom.data.pdf.PdfTitle
import app.chalkroom.feature.pastenotes.PasteNotesContract.PdfFile
import app.chalkroom.feature.pastenotes.PasteNotesContract.PdfProblem
import app.chalkroom.feature.pastenotes.PasteNotesContract.Sheet
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEffect
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiState
import app.chalkroom.shared.NoteLimits
import app.chalkroom.shared.StudentId
import app.chalkroom.students.StudentCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * [preselected] is set when coming from "Teach Mira something else": Continue skips the choice.
 * A PDF is read here, on the device; only the text of one part ever reaches the repository.
 */
class PasteNotesViewModel(
    preselected: StudentId?,
    private val repository: LessonRepository,
    private val reader: PdfReader,
    private val parts: PdfParts,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(
            notes = repository.notes,
            preselected = preselected,
            lastTopic = repository.state.value.lastTaught?.topicTitle,
            studentName = preselected?.let { StudentCatalog[it].name },
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Bumped whenever the file on the sheet is dropped or replaced, so late progress from it is ignored. */
    private val attempt = AtomicInteger()
    private var job: Job? = null
    private var lastRange: IntRange? = null

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.NotesChanged -> {
                _state.update { it.copy(notes = event.notes) }
                repository.setNotes(event.notes)
            }
            UiEvent.UseLastNotes -> {
                val last = repository.state.value.lastTaught ?: return
                _state.update { it.copy(notes = last.notes) }
                repository.setNotes(last.notes)
            }
            UiEvent.Continue -> onContinue()
            is UiEvent.PdfPicked -> pick(event.uri)
            UiEvent.DropFile -> dropFile()
            UiEvent.Retry -> retry()
            is UiEvent.ReadPages -> {
                val asking = _state.value.sheet as? Sheet.AskingPages ?: return
                if (!validRange(event.from, event.to, asking.pageCount)) return
                launchRead(asking.file, event.from..event.to)
            }
        }
    }

    private fun onContinue() {
        val s = _state.value
        if (!s.canContinue) return
        when (val sheet = s.sheet) {
            is Sheet.Ready -> if (sheet.parts.size > 1) {
                parts.offer = PartsOffer(sheet.parts, s.preselected)
                send(UiEffect.PickPart)
                return
            } else {
                repository.setNotes(sheet.parts.single().text)
            }
            else -> repository.setNotes(s.notes.trim())
        }
        send(s.preselected?.let { UiEffect.Read(it) } ?: UiEffect.ChooseStudent)
    }

    private fun pick(uri: String) {
        cancelRead()
        lastRange = null
        val id = attempt.get()
        job = viewModelScope.launch {
            val info = reader.info(uri)
            val file = PdfFile(uri, info.name?.let(PdfTitle::fromFileName))
            if (info.size != null && info.size > PdfLimits.MAX_FILE_BYTES) {
                setSheet(id, Sheet.Failed(file, PdfProblem.TOO_BIG))
                return@launch
            }
            setSheet(id, Sheet.Opening(file))
            when (val opened = reader.open(uri)) {
                PdfOpenResult.Locked -> setSheet(id, Sheet.Failed(file, PdfProblem.LOCKED))
                PdfOpenResult.Unreadable -> setSheet(id, Sheet.Failed(file, PdfProblem.UNREADABLE))
                is PdfOpenResult.Opened -> when {
                    opened.pageCount == 0 -> setSheet(id, Sheet.Failed(file, PdfProblem.NO_WORDS))
                    opened.pageCount > PdfLimits.MAX_PAGES -> setSheet(id, Sheet.AskingPages(file, opened.pageCount))
                    else -> read(id, file, 1..opened.pageCount)
                }
            }
        }
    }

    private fun launchRead(file: PdfFile, range: IntRange) {
        cancelRead()
        val id = attempt.get()
        job = viewModelScope.launch { read(id, file, range) }
    }

    private suspend fun read(id: Int, file: PdfFile, range: IntRange) {
        lastRange = range
        val total = range.last - range.first + 1
        setSheet(id, Sheet.Reading(file, done = 0, total = total, attempt = id))
        val pages = try {
            reader.readPages(file.uri, range) { done ->
                // From the reader's thread: only for the read that's still on the sheet.
                _state.update { s ->
                    val reading = s.sheet as? Sheet.Reading
                    if (reading != null && reading.attempt == id && attempt.get() == id) s.copy(sheet = reading.copy(done = done)) else s
                }
            }
        } catch (e: IOException) {
            setSheet(id, Sheet.Failed(file, PdfProblem.UNREADABLE))
            return
        }
        val cut = withContext(work) {
            val cleaned = PageCleaner.clean(pages)
            if (PageCleaner.join(cleaned).length < NoteLimits.MIN_CHARS) null else PartCutter.cut(cleaned)
        }
        setSheet(id, if (cut.isNullOrEmpty()) Sheet.Failed(file, PdfProblem.NO_WORDS) else Sheet.Ready(file, total, cut))
    }

    private fun retry() {
        val failed = _state.value.sheet as? Sheet.Failed ?: return
        if (!failed.problem.retry) return
        val range = lastRange
        if (range != null) launchRead(failed.file, range) else pick(failed.file.uri)
    }

    /** Back to the editor with whatever was typed before. */
    private fun dropFile() {
        cancelRead()
        lastRange = null
        _state.update { it.copy(sheet = Sheet.Typing) }
        repository.setNotes(_state.value.notes)
    }

    private fun cancelRead() {
        attempt.incrementAndGet()
        job?.cancel()
        job = null
    }

    private fun setSheet(id: Int, sheet: Sheet) {
        if (attempt.get() == id) _state.update { it.copy(sheet = sheet) }
    }

    private fun send(effect: UiEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /** A PDF's text set for Continue never lingers as the typed draft once this screen is gone. */
    override fun onCleared() {
        if (!_state.value.typing && repository.notes != _state.value.notes) repository.setNotes(_state.value.notes)
    }
}
