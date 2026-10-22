package app.chalkroom.data.pdf

import app.chalkroom.shared.NoteLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PartCutterTest {

    private fun sentence(random: Random): String {
        val words = List(random.nextInt(6, 18)) { WORDS[random.nextInt(WORDS.size)] }
        return words.joinToString(" ").replaceFirstChar { it.uppercase() } + "."
    }

    private fun paragraph(random: Random, sentences: Int = random.nextInt(2, 7)) = List(sentences) { sentence(random) }.joinToString(" ")

    /** About [chars] of prose in pages, with a heading every few paragraphs. */
    private fun prose(chars: Int, seed: Int = 1, headings: Boolean = true): List<PdfPage> {
        val random = Random(seed)
        val pages = mutableListOf<PdfPage>()
        var total = 0
        var n = 1
        while (total < chars) {
            val paragraphs = mutableListOf<String>()
            if (headings && n % 2 == 1) paragraphs += "Chapter $n Heading"
            repeat(3) { paragraphs += paragraph(random) }
            val text = paragraphs.joinToString("\n\n")
            pages += PdfPage(n++, text)
            total += text.length + 2
        }
        return pages
    }

    private fun assertWithinLimits(parts: List<Part>) {
        parts.forEach {
            assertTrue(it.text.length <= NoteLimits.MAX_CHARS, "part ${it.index} is ${it.text.length}")
            assertTrue(it.text.length >= NoteLimits.MIN_CHARS, "part ${it.index} is ${it.text.length}")
        }
    }

    @Test
    fun `text that fits is one part`() {
        val pages = prose(5_000)
        val parts = PartCutter.cut(pages)
        assertEquals(1, parts.size)
        assertEquals(PageCleaner.join(pages), parts.single().text)
        assertEquals(1, parts.single().firstPage)
        assertEquals(pages.last().number, parts.single().lastPage)
    }

    @Test
    fun `50k characters become seven near-equal parts`() {
        val pages = prose(50_000)
        val total = PageCleaner.join(pages).length
        val parts = PartCutter.cut(pages)
        assertEquals((total + 7_999) / 8_000, parts.size)
        assertEquals(7, parts.size)
        assertWithinLimits(parts)
        val ideal = total / 7.0
        parts.forEach { assertTrue(it.text.length in (ideal * 0.7).toInt()..8_000, "part ${it.index}: ${it.text.length}") }
    }

    @Test
    fun `parts never lose or reorder text`() {
        val pages = prose(30_000, seed = 3)
        val parts = PartCutter.cut(pages)
        val words = { s: String -> s.split(Regex("\\s+")).filter { it.isNotEmpty() } }
        assertEquals(words(PageCleaner.join(pages)), parts.flatMap { words(it.text) })
        assertEquals((1..parts.size).toList(), parts.map { it.index })
    }

    @Test
    fun `every part fits and none is a scrap, for many lengths`() {
        for (chars in listOf(8_100, 9_000, 16_001, 23_999, 31_000, 64_000, 120_000)) {
            assertWithinLimits(PartCutter.cut(prose(chars, seed = chars)))
        }
    }

    @Test
    fun `cuts prefer headings over paragraphs over sentences`() {
        val random = Random(7)
        // Two halves, each of paragraphs; a heading sits a little off the middle.
        val first = List(5) { paragraph(random, 6) }
        val second = List(5) { paragraph(random, 6) }
        val text = (first.take(4) + listOf("The Second Half", first[4]) + second).joinToString("\n\n")
        val parts = PartCutter.cut(listOf(PdfPage(1, text)), budget = text.length * 2 / 3)
        assertEquals(2, parts.size)
        assertTrue(parts[1].text.startsWith("The Second Half"), parts[1].text.take(40))
        assertEquals("The Second Half", parts[1].heading)

        // No heading: the cut falls between paragraphs, not inside one.
        val plain = (first + second).joinToString("\n\n")
        val cut = PartCutter.cut(listOf(PdfPage(1, plain)), budget = plain.length * 2 / 3)
        assertTrue((first + second).any { cut[1].text.startsWith(it) })

        // One long paragraph: the cut falls at a sentence end.
        val one = (first + second).joinToString(" ")
        val sentenceCut = PartCutter.cut(listOf(PdfPage(1, one)), budget = one.length * 2 / 3)
        assertTrue(sentenceCut[0].text.endsWith("."))
    }

    @Test
    fun `a single 20k-character sentence is still cut`() {
        val random = Random(9)
        val sentence = generateSequence { WORDS[random.nextInt(WORDS.size)] }.take(4_000).joinToString(" ").take(20_000)
        val parts = PartCutter.cut(listOf(PdfPage(1, sentence)))
        assertEquals(3, parts.size)
        assertWithinLimits(parts)
        assertTrue(parts[0].heading.endsWith("…"))
        assertTrue(parts[0].heading.length <= 81)
    }

    @Test
    fun `a CJK sample is cut at sentence ends`() {
        val sentence = "光合作用は植物が光のエネルギーを使って二酸化炭素と水から糖を作る仕組みである。"
        val text = List(600) { sentence }.joinToString("")
        val parts = PartCutter.cut(listOf(PdfPage(4, text)))
        assertTrue(parts.size >= 3)
        assertWithinLimits(parts)
        parts.dropLast(1).forEach { assertTrue(it.text.endsWith("。")) }
        assertEquals(4, parts[1].firstPage)
    }

    @Test
    fun `a CJK sentence end keeps its closing bracket`() {
        val spans = PartCutter.sentences("彼は「はい。」と言った。次の文。")
        assertEquals(listOf("彼は「はい。」", "と言った。", "次の文。"), spans.map { (s, e) -> "彼は「はい。」と言った。次の文。".substring(s, e) })
    }

    @Test
    fun `parts carry their pages and a heading`() {
        val parts = PartCutter.cut(prose(20_000))
        assertEquals(1, parts.first().firstPage)
        parts.zipWithNext().forEach { (a, b) -> assertTrue(a.lastPage!! <= b.firstPage!!) }
        parts.forEach { assertTrue(it.heading.isNotBlank()) }
    }

    private companion object {
        val WORDS = "light water leaf sugar cell energy carbon plant root stem chlorophyll oxygen membrane enzyme reaction cycle glucose sun".split(" ")
    }
}
