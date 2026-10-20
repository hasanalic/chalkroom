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
}
