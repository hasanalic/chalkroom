package app.chalkroom.data.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfTitleTest {
    @Test
    fun `file names read as titles`() {
        assertEquals("Cell Biology — Week 4", PdfTitle.fromFileName("cell_biology_week_4.pdf"))
        assertEquals("Thermo — Part 2", PdfTitle.fromFileName("thermo-part-2.PDF"))
        assertEquals("Photosynthesis Notes", PdfTitle.fromFileName("photosynthesis notes.pdf"))
        assertEquals("Ch 3 Intro", PdfTitle.fromFileName("ch.3.intro.pdf"))
        assertEquals("Week 4", PdfTitle.fromFileName("week_4.pdf"))
        assertEquals("DNA Replication", PdfTitle.fromFileName("DNA_replication.pdf"))
        assertEquals("Untitled notes", PdfTitle.fromFileName("___.pdf"))
        assertEquals("Untitled notes", PdfTitle.fromFileName(".pdf"))
    }

    @Test
    fun `part labels and lengths`() {
        assertEquals("Part 2 · p. 3–6", PdfTitle.partLabel(Part(2, "", "", 3, 6)))
        assertEquals("Part 1 · p. 3", PdfTitle.partLabel(Part(1, "", "", 3, 3)))
        assertEquals("3 words", PdfTitle.length("one two\nthree"))
        assertEquals("1,240 words", PdfTitle.length(List(1_240) { "w" }.joinToString(" ")))
        assertEquals("5 characters", PdfTitle.length("光合 作用だ"))
    }
}
