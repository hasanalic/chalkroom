package app.chalkroom.feature.reading

import kotlin.math.floor
import kotlin.random.Random

/**
 * The notes laid out for the rain: every word in notes order as one strip of glyphs with an empty
 * cell after each word, and the key terms the rain highlights (about 1 in 6 words, never two in a row).
 */
class RainText private constructor(val words: List<String>, val highlighted: BooleanArray) {
    /** Every glyph in order; [GAP] is the empty cell between words. */
    val glyphs: CharArray
    /** The word each glyph belongs to, -1 for a gap. */
    val wordAt: IntArray
    /** Glyph index where each word starts. */
    val wordStarts: IntArray
    /** The letters a flickering glyph swaps to. */
    val letters: CharArray

    init {
        val total = words.sumOf { it.length + 1 }
        glyphs = CharArray(total)
        wordAt = IntArray(total)
        wordStarts = IntArray(words.size)
        var g = 0
        words.forEachIndexed { w, word ->
            wordStarts[w] = g
            word.forEach { ch -> glyphs[g] = ch; wordAt[g] = w; g++ }
            glyphs[g] = GAP; wordAt[g] = -1; g++
        }
        letters = words.flatMap { it.toList() }.distinct().ifEmpty { listOf('a') }.toCharArray()
    }

    /** The highlighted words, each once, in notes order: what flies to the head when the deck arrives. */
    val keyWords: List<String> = words.filterIndexed { i, _ -> highlighted[i] }.distinctBy { it.lowercase() }

    companion object {
        const val GAP = ' '
        const val HIGHLIGHT_SHARE = 1f / 6f
        const val LONG_WORD = 7

        private val WORD = Regex("""[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*""")
        private val SENTENCE_END = Regex("""[.!?:;\n•]""")
        private val STOP = setOf(
            "the", "and", "that", "this", "with", "from", "have", "they", "their", "there", "which", "what",
            "when", "where", "will", "would", "could", "should", "about", "into", "than", "then", "them",
            "these", "those", "more", "most", "some", "such", "also", "only", "other", "very", "just",
            "because", "while", "been", "being", "were", "does", "each", "your", "it's", "over", "many",
            "much", "like", "make", "makes", "made", "both", "between", "through", "after", "before",
        )

        fun from(notes: String, seed: Long = notes.hashCode().toLong()): RainText {
            val matches = WORD.findAll(notes).toList()
            if (matches.isEmpty()) return RainText(listOf("notes"), BooleanArray(1))
            val words = matches.map { it.value }
            val counts = words.groupingBy { it.lowercase() }.eachCount()
            // Key terms first: repeated words, or capitalised in the middle of a sentence.
            val key = matches.indices.filter { i ->
                val w = words[i]
                val lower = w.lowercase()
                val repeated = w.length >= 4 && lower !in STOP && counts.getValue(lower) >= 2
                val midSentence = i > 0 && !SENTENCE_END.containsMatchIn(notes.substring(matches[i - 1].range.last + 1, matches[i].range.first))
                val capital = midSentence && w.first().isUpperCase() && w != "I" && lower !in STOP
                repeated || capital
            }.toSet()
            val long = words.indices.filter { it !in key && words[it].length >= LONG_WORD }
            val random = Random(seed)
            val n = words.size
            val target = Math.round(n * HIGHLIGHT_SHARE)
            val picked = BooleanArray(n)
            var count = 0
            for (i in key.shuffled(random) + long.shuffled(random)) {
                if (count >= target) break
                if (n < 3) break
                // Neighbours wrap: a stream reading past the last word comes back to the first.
                if (picked[(i + n - 1) % n] || picked[(i + 1) % n]) continue
                picked[i] = true
                count++
            }
            return RainText(words, picked)
        }
    }
}

/** Visits one lit cell of the rain. [fade] is 1 at the head and steps down to nothing along the trail. */
fun interface RainCellVisitor {
    fun cell(column: Int, row: Int, glyph: Char, fade: Float, head: Boolean, highlighted: Boolean)
}

/**
 * The rain's model, apart from drawing: one stream per column falling top to bottom, spelling the
 * notes' words in order from a random starting word, each with its own speed, trail and pause.
 * Seeded, so tests and the still frame are repeatable. [frozen] never moves and never flickers.
 */
class NotesRain(
    private val text: RainText,
    /** Rows in each column (columns have different cell sizes, so different row counts). */
    private val rows: IntArray,
    seed: Long,
    val frozen: Boolean = false,
    private val flickerOneIn: Int = 25,
) {
    private val random = Random(seed)
    private val n = rows.size
    private val active = BooleanArray(n)
    private val head = FloatArray(n)
    private val wait = FloatArray(n)
    private val speed = FloatArray(n)
    private val trail = IntArray(n)
    private val start = IntArray(n)
    private val generation = IntArray(n)
    private var time = 0f

    /** Words whose first letter a head has written, and how many of them were highlighted. */
    var wordsFallen = 0
        private set
    var highlightedFallen = 0
        private set

    val columns: Int get() = n

    init {
        for (c in 0 until n) wait[c] = random.nextFloat() * FIRST_DELAY_S
        // Start mid-rain, so the first frame (and the still frame) already has streams in it.
        repeat((PREWARM_S / STEP_S).toInt()) { step(STEP_S) }
    }

    fun advance(seconds: Float) {
        if (frozen || seconds <= 0f) return
        step(seconds)
    }

    /** How many streams this column has started; it grows each time the column restarts from the top. */
    fun generation(column: Int): Int = generation[column]

    /** The head's row, or null while the column waits between streams. */
    fun headRow(column: Int): Float? = if (active[column]) head[column] else null

    fun forEachCell(visit: RainCellVisitor) {
        val size = text.glyphs.size
        for (c in 0 until n) {
            if (!active[c]) continue
            val h = floor(head[c]).toInt()
            val len = trail[c]
            val top = maxOf(0, h - len + 1)
            val bottom = minOf(rows[c] - 1, h)
            for (r in top..bottom) {
                val g = (start[c] + r) % size
                var glyph = text.glyphs[g]
                if (glyph == RainText.GAP) continue
                val d = h - r
                val word = text.wordAt[g]
                if (d > 0 && !frozen && flickerOneIn > 0) {
                    val hash = mix(c, r, generation[c])
                    if (Math.floorMod(hash, flickerOneIn) == 0) {
                        val period = FLICKER_MIN_PERIOD_S + Math.floorMod(hash ushr 8, 1000) / 1000f * FLICKER_PERIOD_SPREAD_S
                        val phase = Math.floorMod(hash ushr 4, 997) / 997f * period
                        val cycle = (time + phase) / period
                        if ((cycle - floor(cycle)) * period < FLICKER_S) {
                            glyph = text.letters[Math.floorMod(hash + cycle.toInt() * 31, text.letters.size)]
                        }
                    }
                }
                visit.cell(c, r, glyph, 1f - d.toFloat() / len, d == 0, text.highlighted[word])
            }
        }
    }

    private fun step(dt: Float) {
        time += dt
        for (c in 0 until n) {
            if (!active[c]) {
                wait[c] -= dt
                if (wait[c] <= 0f) launch(c)
                continue
            }
            val before = floor(head[c]).toInt()
            head[c] += speed[c] * dt
            val after = floor(head[c]).toInt()
            for (r in before + 1..minOf(after, rows[c] - 1)) countWord(c, r)
            if (head[c] - trail[c] >= rows[c]) {
                active[c] = false
                wait[c] = PAUSE_MIN_S + random.nextFloat() * (PAUSE_MAX_S - PAUSE_MIN_S)
            }
        }
    }

    private fun launch(c: Int) {
        active[c] = true
        head[c] = 0f
        speed[c] = rows[c] / (CROSS_MIN_S + random.nextFloat() * (CROSS_MAX_S - CROSS_MIN_S))
        trail[c] = TRAIL_MIN + random.nextInt(TRAIL_MAX - TRAIL_MIN + 1)
        start[c] = text.wordStarts[random.nextInt(text.wordStarts.size)]
        generation[c]++
        countWord(c, 0)
    }

    private fun countWord(c: Int, row: Int) {
        val g = (start[c] + row) % text.glyphs.size
        val word = text.wordAt[g]
        if (word >= 0 && text.wordStarts[word] == g) {
            wordsFallen++
            if (text.highlighted[word]) highlightedFallen++
        }
    }

    private fun mix(a: Int, b: Int, c: Int): Int {
        var h = a * 73856093 xor b * 19349663 xor c * 83492791
        h = h xor (h ushr 13)
        h *= 0x5bd1e995
        return h xor (h ushr 15)
    }

    companion object {
        const val CROSS_MIN_S = 1.5f
        const val CROSS_MAX_S = 4f
        const val TRAIL_MIN = 8
        const val TRAIL_MAX = 20
        const val FIRST_DELAY_S = 3f
        const val PAUSE_MIN_S = 0.2f
        const val PAUSE_MAX_S = 2.2f
        const val FLICKER_S = 0.08f
        const val FLICKER_MIN_PERIOD_S = 0.5f
        const val FLICKER_PERIOD_SPREAD_S = 1.2f
        private const val PREWARM_S = 2.5f
        private const val STEP_S = 1f / 30f
    }
}
