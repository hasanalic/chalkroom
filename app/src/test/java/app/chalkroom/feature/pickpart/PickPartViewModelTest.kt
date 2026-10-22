package app.chalkroom.feature.pickpart

import app.chalkroom.data.LessonRepository
import app.chalkroom.data.pdf.Part
import app.chalkroom.data.pdf.PartsOffer
import app.chalkroom.data.pdf.PdfParts
import app.chalkroom.feature.pickpart.PickPartContract.UiEffect
import app.chalkroom.feature.pickpart.PickPartContract.UiEvent
import app.chalkroom.shared.StudentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickPartViewModelTest {
    private val parts = listOf(Part(1, "first part text", "First", 1, 2), Part(2, "second part text", "Second", 3, 4))
    private val repository = LessonRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(preselected: StudentId? = null) =
        PickPartViewModel(repository, PdfParts().apply { offer = PartsOffer(parts, preselected) })

    @Test
    fun `the first part starts selected and the picked part becomes the notes`() = runTest {
        val vm = viewModel()
        assertEquals(0, vm.state.value.selected)
        vm.onEvent(UiEvent.Selected(1))
        vm.onEvent(UiEvent.Confirm)
        assertEquals(UiEffect.ChooseStudent, vm.effects.first())
        assertEquals("second part text", repository.notes)
    }

    @Test
    fun `with a preselected student it goes straight to reading, in their voice`() = runTest {
        val vm = viewModel(StudentId.MIRA)
        assertEquals("Mira", vm.state.value.studentName)
        assertEquals("That's more than I can take in one sitting. Which part are we doing?", pickPartHeader("Mira"))
        assertEquals("That's more than one sitting. Which part are we doing?", pickPartHeader(null))
        vm.onEvent(UiEvent.Confirm)
        assertEquals(UiEffect.Read(StudentId.MIRA), vm.effects.first())
        assertEquals("first part text", repository.notes)
    }
}
