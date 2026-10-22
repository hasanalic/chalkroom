package app.chalkroom.data.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageCleanerTest {
    private val prose = "Plants make their own food from light, water and carbon dioxide in the leaves."

    @Test
    fun `a page with too few characters is skipped, the rest is kept`() {
        val pages = listOf(PdfPage(1, "  12  "), PdfPage(2, prose))
        assertEquals(listOf(2), PageCleaner.clean(pages).map { it.number })
    }

    @Test
    fun `a page of broken characters is skipped`() {
        val broken = "�".repeat(10) + prose
        assertFalse(PageCleaner.isReadable(broken))
        assertTrue(PageCleaner.isReadable(prose + "�"))
    }

    @Test
    fun `running headers and footers are dropped from every page, page numbers or not`() {
        val pages = (1..5).map { PdfPage(it, "Cell Biology Notes\n$prose\nPage $it of 5") }
        val cleaned = PageCleaner.clean(pages)
        assertEquals(5, cleaned.size)
        cleaned.forEach { assertEquals(prose, it.text) }
    }

    @Test
    fun `with two pages nothing is taken for a header`() {
        val pages = (1..2).map { PdfPage(it, "Cell Biology Notes\n\n$prose") }
        assertTrue(PageCleaner.clean(pages).all { it.text.startsWith("Cell Biology Notes") })
    }

    @Test
    fun `a line-end hyphen before a lowercase word joins the word`() {
        assertEquals("the photosynthesis step", PageCleaner.joinHyphens("the photo-\nsynthesis step"))
        assertEquals("North-America", PageCleaner.joinHyphens("North-\nAmerica"))
    }

    @Test
    fun `a German suspended hyphen is kept`() {
        assertEquals("Ein- und Ausgang", PageCleaner.joinHyphens("Ein-\nund Ausgang"))
        assertEquals("Vor- oder Nachteile", PageCleaner.joinHyphens("Vor-\noder Nachteile"))
    }

    @Test
    fun `French compounds keep their hyphen on a French page`() {
        val french = "Il est dans la classe et elle est avec le professeur. Peut-\nêtre que la leçon est sur celle-\nci, c'est-\nà-dire la photosynthèse. A-t-\nil faim ?"
        val joined = PageCleaner.joinHyphens(french)
        assertTrue("Peut-être" in joined, joined)
        assertTrue("celle-ci" in joined, joined)
        assertTrue("c'est-à-dire" in joined, joined)
        assertTrue("A-t-il" in joined, joined)
        // On an English page, the same tail joins.
        assertEquals("Peutêtre is not English", PageCleaner.joinHyphens("Peut-\nêtre is not English"))
    }

    @Test
    fun `single line breaks are layout, blank lines are paragraphs`() {
        val text = "Light reactions take place in the thylakoid membranes of the\nchloroplast and need water.\n\nThe Calvin cycle builds sugar from carbon dioxide in\nthe stroma."
        assertEquals(
            listOf(
                "Light reactions take place in the thylakoid membranes of the chloroplast and need water.",
                "The Calvin cycle builds sugar from carbon dioxide in the stroma.",
            ),
            PageCleaner.paragraphs(text),
        )
    }

    @Test
    fun `a heading at the top of a block stays its own paragraph`() {
        val text = "Photosynthesis\nPlants capture light energy with chlorophyll and store it as sugar for\nlater use in the cell."
        assertEquals(
            listOf("Photosynthesis", "Plants capture light energy with chlorophyll and store it as sugar for later use in the cell."),
            PageCleaner.paragraphs(text),
        )
    }

    @Test
    fun `CJK lines join without a space`() {
        assertEquals(listOf("光合作用は葉緑体で行われる。"), PageCleaner.paragraphs("光合作用は葉緑体で\n行われる。"))
    }

    @Test
    fun `spaces, tabs and NBSPs collapse`() {
        assertEquals(listOf("a b c d"), PageCleaner.paragraphs("  a \t b  c   d  "))
    }

    @Test
    fun `heading rule counts CJK as two wide and refuses sentence ends`() {
        assertTrue(looksLikeHeading("Cell Biology"))
        assertFalse(looksLikeHeading("This ends like a sentence."))
        assertFalse(looksLikeHeading("見出し："))
        assertFalse(looksLikeHeading("1234"))
        assertFalse(looksLikeHeading("光".repeat(41)))
        assertTrue(looksLikeHeading("光".repeat(40)))
    }
}
