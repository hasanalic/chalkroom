package app.chalkroom.feature.pastenotes

import app.chalkroom.data.LessonRepository
import app.chalkroom.data.pdf.PdfFileInfo
import app.chalkroom.data.pdf.PdfOpenResult
import app.chalkroom.data.pdf.PdfPage
import app.chalkroom.data.pdf.PdfParts
import app.chalkroom.data.pdf.PdfReadException
import app.chalkroom.data.pdf.PdfReader
import app.chalkroom.feature.pastenotes.PasteNotesContract.PdfProblem
import app.chalkroom.feature.pastenotes.PasteNotesContract.Sheet
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEffect
import app.chalkroom.feature.pastenotes.PasteNotesContract.UiEvent
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasteNotesViewModelPdfTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Different on every page, so nothing looks like a running header or footer. */
    private fun prose(page: Int, chars: Int = 600): String {
        val word = NAMES[page % NAMES.size]
        return "About $word\n\n" + "Plants make sugar from light and water in their leaves.\n".repeat(chars / 56 + 1).take(chars) + "\nThat is all about $word."
    }

    /** A reader whose answers each test sets; with [gated], every page waits to be released. */
    private inner class FakeReader : PdfReader {
        var info = PdfFileInfo("cell_biology_week_4.pdf", 1_000)
        var open: PdfOpenResult = PdfOpenResult.Opened(3)
        var pageText: (Int) -> String = { prose(it) }
        var failAt: Int? = null
        var gated = false
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val opened = mutableListOf<String>()
        val ranges = mutableListOf<IntRange>()
        var progress: ((Int) -> Unit)? = null

        override suspend fun info(uri: String) = info
        override suspend fun open(uri: String): PdfOpenResult = open.also { opened += uri }
        override suspend fun readPages(uri: String, pages: IntRange, onPage: (Int) -> Unit): List<PdfPage> {
            ranges += pages
            progress = onPage
            return pages.mapIndexed { i, n ->
                if (gated) gate.receive()
                if (n == failAt) throw PdfReadException(RuntimeException("bad page"))
                onPage(i + 1)
                PdfPage(n, pageText(n))
            }
        }
    }

    private val reader = FakeReader()
    private val repository = LessonRepository()
    private val parts = PdfParts()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(preselected: StudentId? = null) = PasteNotesViewModel(preselected, repository, reader, parts, dispatcher)

    private val PasteNotesViewModel.sheet get() = state.value.sheet

    @Test
    fun `a short PDF is read into one part and Continue sends its text as the notes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        val ready = assertInstanceOf(Sheet.Ready::class.java, vm.sheet)
        assertEquals("Cell Biology — Week 4", ready.file.title)
        assertEquals(3, ready.pageCount)
        assertEquals(1, ready.parts.size)
        assertTrue(vm.state.value.canContinue)

        vm.onEvent(UiEvent.Continue)
        assertEquals(UiEffect.ChooseStudent, vm.effects.first())
        assertEquals(ready.parts.single().text, repository.notes)
    }

    @Test
    fun `with a preselected student Continue goes straight to reading`() = runTest(dispatcher) {
        val vm = viewModel(StudentId.MIRA)
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        vm.onEvent(UiEvent.Continue)
        assertEquals(UiEffect.Read(StudentId.MIRA), vm.effects.first())
    }

    @Test
    fun `a long PDF is cut into parts and Continue asks which one`() = runTest(dispatcher) {
        reader.open = PdfOpenResult.Opened(10)
        reader.pageText = { prose(it, 3_000) }
        val vm = viewModel(StudentId.PIP)
        vm.onEvent(UiEvent.PdfPicked("content://long"))
        val ready = assertInstanceOf(Sheet.Ready::class.java, vm.sheet)
        assertTrue(ready.parts.size > 1)
        vm.onEvent(UiEvent.Continue)
        assertEquals(UiEffect.PickPart, vm.effects.first())
        assertEquals(ready.parts, parts.offer?.parts)
        assertEquals(StudentId.PIP, parts.offer?.preselected)
        assertEquals("", repository.notes)
    }

    @Test
    fun `reading shows progress page by page and Continue stays off`() = runTest(dispatcher) {
        reader.gated = true
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        assertEquals(Sheet.Reading(vm.sheet.let { (it as Sheet.Reading).file }, 0, 3, (vm.sheet as Sheet.Reading).attempt), vm.sheet)
        assertFalse(vm.state.value.canContinue)
        assertTrue(vm.state.value.busy)
        reader.gate.send(Unit)
        assertEquals(1, (vm.sheet as Sheet.Reading).done)
        reader.gate.send(Unit)
        assertEquals(2, (vm.sheet as Sheet.Reading).done)
        reader.gate.send(Unit)
        assertInstanceOf(Sheet.Ready::class.java, vm.sheet)
    }

    @Test
    fun `stop drops the file, typed text comes back, and late progress is ignored`() = runTest(dispatcher) {
        reader.gated = true
        val vm = viewModel()
        vm.onEvent(UiEvent.NotesChanged("   "))
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        reader.gate.send(Unit)
        val stale = reader.progress!!
        vm.onEvent(UiEvent.DropFile)
        assertEquals(Sheet.Typing, vm.sheet)
        assertEquals("   ", vm.state.value.notes)
        assertEquals("   ", repository.notes)
        stale(2)
        assertEquals(Sheet.Typing, vm.sheet)
    }

    @Test
    fun `progress from a stopped read never reaches the next read of the same file`() = runTest(dispatcher) {
        reader.gated = true
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        val stale = reader.progress!!
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        assertEquals(0, (vm.sheet as Sheet.Reading).done)
        stale(3)
        assertEquals(0, (vm.sheet as Sheet.Reading).done)
    }

    @Test
    fun `paste text instead after Continue puts the typed draft back`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        vm.onEvent(UiEvent.Continue)
        vm.effects.first()
        vm.onEvent(UiEvent.DropFile)
        assertEquals("", repository.notes)
        assertTrue(vm.state.value.offerPdf)
    }

    @Test
    fun `a file over 50 MB is refused before opening`() = runTest(dispatcher) {
        reader.info = PdfFileInfo("big.pdf", 51L * 1024 * 1024)
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://big"))
        assertEquals(PdfProblem.TOO_BIG, (vm.sheet as Sheet.Failed).problem)
        assertTrue(reader.opened.isEmpty())
    }

    @Test
    fun `a missing size skips the check and a missing name leaves no title`() = runTest(dispatcher) {
        reader.info = PdfFileInfo(null, null)
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        assertEquals(null, (vm.sheet as Sheet.Ready).file.title)
    }

    @Test
    fun `locked and unreadable files fail calmly, only unreadable offers a retry`() = runTest(dispatcher) {
        reader.open = PdfOpenResult.Locked
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://locked"))
        assertEquals(PdfProblem.LOCKED, (vm.sheet as Sheet.Failed).problem)
        assertFalse(PdfProblem.LOCKED.retry)

        reader.open = PdfOpenResult.Unreadable
        vm.onEvent(UiEvent.PdfPicked("content://broken"))
        assertEquals(PdfProblem.UNREADABLE, (vm.sheet as Sheet.Failed).problem)

        reader.open = PdfOpenResult.Opened(2)
        vm.onEvent(UiEvent.Retry)
        assertInstanceOf(Sheet.Ready::class.java, vm.sheet)
        assertEquals(listOf("content://locked", "content://broken", "content://broken"), reader.opened)
    }

    @Test
    fun `a read that throws is unreadable and retry reads the same range again`() = runTest(dispatcher) {
        reader.open = PdfOpenResult.Opened(400)
        reader.failAt = 5
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        vm.onEvent(UiEvent.ReadPages(3, 22))
        assertEquals(PdfProblem.UNREADABLE, (vm.sheet as Sheet.Failed).problem)
        reader.failAt = null
        vm.onEvent(UiEvent.Retry)
        assertEquals(listOf(3..22, 3..22), reader.ranges)
        assertEquals(20, (vm.sheet as Sheet.Ready).pageCount)
    }

    @Test
    fun `more than 150 pages asks which ones first and reads only that range`() = runTest(dispatcher) {
        reader.open = PdfOpenResult.Opened(412)
        reader.gated = true
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        assertEquals(412, (vm.sheet as Sheet.AskingPages).pageCount)
        assertTrue(reader.ranges.isEmpty())

        vm.onEvent(UiEvent.ReadPages(1, 151))
        vm.onEvent(UiEvent.ReadPages(20, 10))
        assertInstanceOf(Sheet.AskingPages::class.java, vm.sheet)

        vm.onEvent(UiEvent.ReadPages(1, 20))
        assertEquals(20, (vm.sheet as Sheet.Reading).total)
        assertEquals(listOf(1..20), reader.ranges)
    }

    @Test
    fun `never mind empties the sheet`() = runTest(dispatcher) {
        reader.open = PdfOpenResult.Opened(412)
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        vm.onEvent(UiEvent.DropFile)
        assertEquals(Sheet.Typing, vm.sheet)
    }

    @Test
    fun `scanned pages leave no words`() = runTest(dispatcher) {
        reader.pageText = { " \n 3 \n" }
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://scan"))
        assertEquals(PdfProblem.NO_WORDS, (vm.sheet as Sheet.Failed).problem)
    }

    @Test
    fun `choosing another PDF replaces the one being read`() = runTest(dispatcher) {
        reader.gated = true
        val vm = viewModel()
        vm.onEvent(UiEvent.PdfPicked("content://a"))
        reader.info = PdfFileInfo("second.pdf", 10)
        reader.gated = false
        vm.onEvent(UiEvent.PdfPicked("content://b"))
        assertEquals("Second", (vm.sheet as Sheet.Ready).file.title)
    }

    @Test
    fun `the PDF offer shows only while the sheet is empty`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.offerPdf)
        vm.onEvent(UiEvent.NotesChanged("Photosynthesis"))
        assertFalse(vm.state.value.offerPdf)
    }

    @Test
    fun `range rule and the named lines`() {
        assertTrue(validRange(1, 150, 412))
        assertFalse(validRange(1, 151, 412))
        assertFalse(validRange(0, 10, 412))
        assertFalse(validRange(10, 9, 412))
        assertFalse(validRange(400, 413, 412))
        assertFalse(validRange(null, 3, 412))
        assertEquals("Mira couldn't read this file.", problemLine(PdfProblem.UNREADABLE, "Mira"))
        assertEquals("That file is too heavy. Try a smaller one.", problemLine(PdfProblem.TOO_BIG, null))
        assertEquals("Reading page 3 of 12", pillText(Sheet.Reading(PasteNotesContract.PdfFile("x", null), 2, 12), null))
        assertEquals("Mira is opening it…", pillText(Sheet.Opening(PasteNotesContract.PdfFile("x", null)), "Mira"))
        assertNotNull(PdfProblem.entries.single { it.retry })
    }

    private companion object {
        val NAMES = listOf("roots", "stems", "leaves", "seeds", "flowers", "bark", "sap", "buds", "fruit", "moss", "ferns", "pollen")
    }
}
