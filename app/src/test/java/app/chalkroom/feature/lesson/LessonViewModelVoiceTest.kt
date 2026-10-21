package app.chalkroom.feature.lesson

import app.chalkroom.data.ChalkroomApi
import app.chalkroom.data.LessonRepository
import app.chalkroom.feature.lesson.LessonContract.Phase
import app.chalkroom.feature.lesson.LessonContract.UiEvent
import app.chalkroom.shared.AppJson
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.MisconceptionDto
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnLimits
import app.chalkroom.shared.TurnRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** Voice mode is only another way to write the input: it never sends and never touches the round. */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelVoiceTest {
    private val deck = CreateDeckResponse(
        language = "en",
        topicTitle = "Photosynthesis",
        misconceptions = listOf(
            MisconceptionDto("m1", "Plants eat soil.", "Plants make their own food."),
            MisconceptionDto("m2", "Light is heat.", "Light energy is captured by chlorophyll."),
        ),
    )

    /** Every turn request is recorded and its reply held forever, so a sent turn stays THINKING. */
    private val requests = AtomicInteger()
    private val sentBody = CompletableDeferred<TurnRequest>()
    private val engine = MockEngine { request ->
        requests.incrementAndGet()
        sentBody.complete(AppJson.decodeFromString(TurnRequest.serializer(), (request.body as TextContent).text))
        CompletableDeferred<Unit>().await()
        respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val api = ChalkroomApi(HttpClient(engine) { install(ContentNegotiation) { json(AppJson) } }, "http://test")
    private val repository = LessonRepository().apply { startLesson(StudentId.MIRA, deck) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LessonViewModel(StudentId.MIRA, api, repository)

    private val LessonViewModel.voice get() = state.value.voice

    @Test
    fun `the mic opens voice mode starting with nothing heard`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.OpenVoice)
        assertEquals(VoicePhase.STARTING, vm.voice?.phase)
        assertEquals("", vm.voice?.transcript)
    }

    @Test
    fun `the mic does nothing while the input is locked`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.InputChanged("Plants make sugar from light."))
        vm.onEvent(UiEvent.Send)
        assertEquals(Phase.THINKING, vm.state.value.phase)
        vm.onEvent(UiEvent.OpenVoice)
        assertNull(vm.voice)
    }

    @Test
    fun `partials replace each other and finals accumulate across restarts`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceListening)
        vm.onEvent(UiEvent.VoicePartial("pla"))
        vm.onEvent(UiEvent.VoicePartial("plants"))
        assertEquals("plants", vm.voice?.partial)
        vm.onEvent(UiEvent.VoiceFinal("a"))
        assertEquals("", vm.voice?.partial)
        vm.onEvent(UiEvent.VoiceQuiet)
        vm.onEvent(UiEvent.VoiceListening)
        vm.onEvent(UiEvent.VoiceFinal("b"))
        assertEquals("a b", vm.voice?.finalized)
        vm.onEvent(UiEvent.VoicePartial("c"))
        assertEquals("a b c", vm.voice?.transcript)
    }

    @Test
    fun `done appends the transcript after the typed text, partial included`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.InputChanged("Plants  "))
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceFinal("make their own food"))
        vm.onEvent(UiEvent.VoicePartial("from light"))
        vm.onEvent(UiEvent.VoiceDone)
        assertEquals("Plants make their own food from light", vm.state.value.input)
        assertNull(vm.voice)
    }

    @Test
    fun `done is not a send`() {
        val vm = viewModel()
        val before = vm.state.value
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceFinal("plants make food"))
        vm.onEvent(UiEvent.VoiceDone)
        val after = vm.state.value
        assertEquals(0, requests.get())
        assertEquals(Phase.IDLE, after.phase)
        assertEquals(before.expression, after.expression)
        assertEquals(before.lastExplanation, after.lastExplanation)
        assertEquals(before.lesson, after.lesson)
        assertEquals(0, after.lesson!!.exchanges.size)
        assertEquals(2, after.lesson!!.queue.size)
    }

    @Test
    fun `done with nothing heard changes nothing and stays open`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.InputChanged("typed"))
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoicePartial("  "))
        vm.onEvent(UiEvent.VoiceDone)
        assertEquals("typed", vm.state.value.input)
        assertNotNull(vm.voice)
    }

    @Test
    fun `cancel discards what was heard and keeps what was typed`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.InputChanged("typed"))
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceFinal("spoken"))
        vm.onEvent(UiEvent.VoiceCancel)
        assertNull(vm.voice)
        assertEquals("typed", vm.state.value.input)
    }

    @Test
    fun `a refused mic goes to error and retry starts again`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoicePermission(granted = false, blocked = false))
        assertEquals(VoicePhase.ERROR, vm.voice?.phase)
        assertEquals(VoiceError.PERMISSION_DENIED, vm.voice?.error)
        vm.onEvent(UiEvent.VoiceRetry)
        assertEquals(VoicePhase.STARTING, vm.voice?.phase)
        assertNull(vm.voice?.error)
        vm.onEvent(UiEvent.VoicePermission(granted = false, blocked = true))
        assertEquals(VoiceError.PERMISSION_BLOCKED, vm.voice?.error)
        vm.onEvent(UiEvent.VoiceRetry)
        vm.onEvent(UiEvent.VoicePermission(granted = true, blocked = false))
        assertEquals(VoicePhase.STARTING, vm.voice?.phase)
    }

    @Test
    fun `an error keeps the words heard`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceFinal("plants"))
        vm.onEvent(UiEvent.VoicePartial("make"))
        vm.onEvent(UiEvent.VoiceFailed(VoiceError.FAILED))
        assertEquals(VoicePhase.ERROR, vm.voice?.phase)
        assertEquals("plants", vm.voice?.finalized)
        assertEquals("make", vm.voice?.partial)
    }

    @Test
    fun `late recognizer callbacks after cancel are ignored`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceCancel)
        listOf(
            UiEvent.VoicePartial("late"),
            UiEvent.VoiceFinal("late"),
            UiEvent.VoiceListening,
            UiEvent.VoiceQuiet,
            UiEvent.VoiceFailed(VoiceError.FAILED),
            UiEvent.VoiceRetry,
            UiEvent.VoicePermission(granted = false, blocked = true),
        ).forEach {
            vm.onEvent(it)
            assertNull(vm.voice, "$it")
        }
    }

    @Test
    fun `edited text after done goes out through the normal send`() = runTest {
        val vm = viewModel()
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceFinal("plants make food from soil"))
        vm.onEvent(UiEvent.VoiceDone)
        vm.onEvent(UiEvent.InputChanged(vm.state.value.input.replace("soil", "light")))
        vm.onEvent(UiEvent.Send)
        assertEquals("plants make food from light", sentBody.await().explanation)
        assertEquals(Phase.THINKING, vm.state.value.phase)
        assertEquals("plants make food from light", vm.state.value.lastExplanation)
        assertEquals("", vm.state.value.input)
    }

    @Test
    fun `done caps the input at the explanation limit`() {
        val vm = viewModel()
        vm.onEvent(UiEvent.InputChanged("a".repeat(TurnLimits.MAX_EXPLANATION_CHARS - 5)))
        vm.onEvent(UiEvent.OpenVoice)
        vm.onEvent(UiEvent.VoiceFinal("plants make their own food"))
        vm.onEvent(UiEvent.VoiceDone)
        assertEquals(TurnLimits.MAX_EXPLANATION_CHARS, vm.state.value.input.length)
        assertEquals("a".repeat(TurnLimits.MAX_EXPLANATION_CHARS - 5) + " plan", vm.state.value.input)
    }
}
