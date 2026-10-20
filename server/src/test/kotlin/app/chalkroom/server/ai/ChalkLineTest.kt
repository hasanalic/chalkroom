package app.chalkroom.server.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChalkLineTest {

    @Test
    fun `quotes, end stop and extra spaces go`() {
        assertEquals("light powers making glucose", ChalkLine.clean("  \"light   powers making glucose.\" "))
    }

    @Test
    fun `blank or runaway lines become no line`() {
        assertNull(ChalkLine.clean("  "))
        assertNull(ChalkLine.clean("one two three four five six seven eight nine ten eleven"))
    }

    @Test
    fun `lines start lowercase, even sun or earth`() {
        assertEquals("sun is higher and hits more directly", ChalkLine.clean("Sun is higher and hits more directly"))
        assertEquals("earth tilts toward the sun", ChalkLine.clean("Earth tilts toward the sun", "no bc the Earth tilts"))
        assertEquals("light energy splits water", ChalkLine.clean("Light energy splits water"))
    }

    @Test
    fun `a name the user wrote, I, or an acronym keeps its capital`() {
        assertEquals("Newton said gravity pulls down", ChalkLine.clean("Newton said gravity pulls down", "like Newton said, gravity pulls down"))
        assertEquals("light splits water", ChalkLine.clean("Light splits water", "Light splits water. no other way"))
        assertEquals("DNA carries the code", ChalkLine.clean("DNA carries the code"))
        assertEquals("I think light splits water", ChalkLine.clean("I think light splits water"))
    }
}
