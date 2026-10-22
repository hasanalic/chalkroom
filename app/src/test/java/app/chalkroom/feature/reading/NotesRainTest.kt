package app.chalkroom.feature.reading

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotesRainTest {
    private val notes = """
        Photosynthesis happens in the chloroplasts of plant cells. Chlorophyll absorbs light, mostly red and
        blue, and reflects green. The light reactions split water and release oxygen; the Calvin cycle then
        fixes carbon dioxide into sugar. Without light the Calvin cycle soon stops, because it needs the ATP
        and NADPH that the light reactions make. Plants also respire, day and night, using some of the sugar.
        Stomata on the leaves open to let carbon dioxide in and water vapour out, which is why plants wilt
        on hot days when they close them. Farmers in Kenya and Brazil grow maize, which uses a different
        pathway that copes better with heat and keeps stomata closed for longer.
    """.trimIndent()
    private val text = RainText.from(notes, seed = 7)
    private val rows = IntArray(30) { 40 + it % 3 * 12 }

    /** Each column's lit cells top to bottom, cut into words at the gaps (the top and bottom ones may be partial). */
    private fun streams(rain: NotesRain): Map<Int, List<Pair<String, Boolean>>> {
        val cells = sortedMapOf<Int, MutableList<Triple<Int, Char, Boolean>>>()
        rain.forEachCell { column, row, glyph, _, _, highlighted -> cells.getOrPut(column) { mutableListOf() } += Triple(row, glyph, highlighted) }
        return cells.mapValues { (_, list) ->
            val words = mutableListOf<Pair<String, Boolean>>()
            var word = StringBuilder()
            var lit = false
            var lastRow = -2
            for ((row, glyph, highlighted) in list.sortedBy { it.first }) {
                if (row != lastRow + 1 && word.isNotEmpty()) { words += word.toString() to lit; word = StringBuilder() }
                word.append(glyph)
                lit = highlighted
                lastRow = row
            }
            if (word.isNotEmpty()) words += word.toString() to lit
            words
        }
    }

    @Test
    fun `words come out in notes order inside a stream`() {
        val rain = NotesRain(text, rows, seed = 1, flickerOneIn = 0)
        var checked = 0
        repeat(120) {
            rain.advance(0.05f)
            streams(rain).values.forEach { words ->
                // Inner words are whole; consecutive whole words must follow each other in the notes.
                val whole = words.drop(1).dropLast(1).map { it.first }
                whole.zipWithNext().forEach { (a, b) ->
                    val follows = text.words.indices.any { i -> text.words[i] == a && text.words[(i + 1) % text.words.size] == b }
                    assertTrue(follows, "\"$b\" does not follow \"$a\" in the notes")
                    checked++
                }
            }
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `streams restart from the top after leaving the bottom`() {
        val rain = NotesRain(text, rows, seed = 2)
        val first = IntArray(rain.columns) { rain.generation(it) }
        var sawRestartAtTop = false
        repeat(400) {
            val before = IntArray(rain.columns) { rain.generation(it) }
            rain.advance(0.05f)
            for (c in 0 until rain.columns) {
                if (rain.generation(c) > before[c]) {
                    val head = rain.headRow(c)
                    assertNotNull(head)
                    assertTrue(head!! < 2f, "a restarted stream begins at the top")
                    sawRestartAtTop = true
                }
            }
        }
        assertTrue(sawRestartAtTop)
        for (c in 0 until rain.columns) assertTrue(rain.generation(c) >= first[c] + 2, "column $c keeps restarting")
    }

    @Test
    fun `highlighted words are never adjacent in the same stream`() {
        val n = text.words.size
        for (i in 0 until n) assertFalse(text.highlighted[i] && text.highlighted[(i + 1) % n], "words $i and ${i + 1} both highlighted")
        val rain = NotesRain(text, rows, seed = 3, flickerOneIn = 0)
        repeat(200) {
            rain.advance(0.05f)
            streams(rain).values.forEach { words ->
                words.zipWithNext().forEach { (a, b) -> assertFalse(a.second && b.second, "\"${a.first}\" and \"${b.first}\" touch") }
            }
        }
    }

    @Test
    fun `about one in six falling words is highlighted, key terms first`() {
        val rain = NotesRain(text, rows, seed = 4)
        repeat(2_000) { rain.advance(0.05f) }
        val ratio = rain.highlightedFallen.toFloat() / rain.wordsFallen
        assertTrue(rain.wordsFallen > 500)
        assertTrue(ratio in 0.11f..0.23f, "ratio $ratio")
        // Repeated or mid-sentence capitalised words are chosen before plain long words.
        val keyTerms = setOf("photosynthesis", "light", "calvin", "cycle", "sugar", "stomata", "carbon", "dioxide", "water", "plants", "reactions", "kenya", "brazil", "atp", "nadph")
        assertTrue(text.keyWords.isNotEmpty())
        assertTrue(text.keyWords.count { it.lowercase() in keyTerms } >= text.keyWords.size / 2, "${text.keyWords}")
    }

    @Test
    fun `reduced motion is one frozen frame`() {
        val rain = NotesRain(text, rows, seed = 5, frozen = true)
        fun frame(): List<String> = buildList { rain.forEachCell { c, r, g, f, h, l -> add("$c:$r:$g:$f:$h:$l") } }
        val before = frame()
        assertTrue(before.isNotEmpty(), "the still frame shows rain")
        rain.advance(5f)
        assertEquals(before, frame())
    }

    @Test
    fun `short notes still rain`() {
        val short = RainText.from("Cells divide.")
        val rain = NotesRain(short, IntArray(5) { 30 }, seed = 6)
        rain.advance(2f)
        var lit = 0
        rain.forEachCell { _, _, _, _, _, _ -> lit++ }
        assertTrue(lit > 0)
    }
}
