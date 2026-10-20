package app.chalkroom.server.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `only a plain a-z line in a lettered language is respelled`() {
        assertTrue(ChalkLine.mayHaveLostLetters("tr", "isik enerjisi lazim"))
        assertTrue(ChalkLine.mayHaveLostLetters("de-DE", "Licht ist fur Pflanzen notig"))
        assertFalse(ChalkLine.mayHaveLostLetters("tr", "ışık enerjisi lazım"))
        assertFalse(ChalkLine.mayHaveLostLetters("en", "light energy splits water"))
    }

    @Test
    fun `a respell is kept only when nothing but letters changed`() {
        assertEquals("ışık enerjisi lazım", ChalkLine.acceptRespell("isik enerjisi lazim", "ışık enerjisi lazım."))
        assertEquals("İçin ışık lazım", ChalkLine.acceptRespell("Icin isik lazim", "İçin ışık lazım"))
        assertNull(ChalkLine.acceptRespell("isik enerjisi lazim", "klorofilin ışık enerjisi lazım"))
        assertNull(ChalkLine.acceptRespell("isik enerjisi lazim", "ışık enerjisi gerekir"))
        assertNull(ChalkLine.acceptRespell("isik enerjisi lazim", "isik enerjisi lazim"))
    }
}
