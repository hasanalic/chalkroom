package app.chalkroom.feature.lesson

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MergeSpokenTest {
    @Test
    fun `exactly one space between typed and spoken`() {
        assertEquals("plants make food", mergeSpoken("plants", "  make food "))
    }

    @Test
    fun `blank spoken changes nothing`() {
        assertEquals("plants  ", mergeSpoken("plants  ", "   "))
        assertEquals("", mergeSpoken("", ""))
    }

    @Test
    fun `blank existing returns the spoken text`() {
        assertEquals("make food", mergeSpoken("  ", " make food"))
    }

    @Test
    fun `trailing spaces on existing don't double`() {
        assertEquals("plants make", mergeSpoken("plants   \n", "make"))
    }

    @Test
    fun `transcript joins finalized and partial with one space, skipping blanks`() {
        assertEquals("a b", VoiceInput(finalized = "a", partial = "b").transcript)
        assertEquals("b", VoiceInput(finalized = " ", partial = "b").transcript)
        assertEquals("a", VoiceInput(finalized = "a", partial = "").transcript)
    }
}
