package app.chalkroom.data.pdf

import app.chalkroom.shared.NoteLimits
import kotlin.math.abs

/** One sitting's worth of a PDF. [index] starts at 1; pages are the first and last PDF page it touches. */
data class Part(val index: Int, val text: String, val heading: String, val firstPage: Int?, val lastPage: Int?)

/**
 * Cuts cleaned pages into parts of about equal length, each at most [NoteLimits.MAX_CHARS]. Cuts fall
 * only between pieces (paragraphs, or sentences and word runs of an over-long paragraph), preferring
 * a heading, then a paragraph break, then a sentence end.
 */
object PartCutter {

    /** Best first. */
    internal enum class Rating { HEADING, PARAGRAPH, SENTENCE, HARD }

    /** A piece of the text, [start] until [end] in the joined text; [rating] rates the cut before it. */
    private class Piece(val start: Int, val end: Int, val page: Int?, val rating: Rating)

    fun cut(pages: List<PdfPage>, budget: Int = NoteLimits.MAX_CHARS): List<Part> {
        val (text, pieces) = layout(pages, budget)
        if (pieces.isEmpty()) return emptyList()
        if (text.length <= budget) return listOf(part(1, text, pieces, 0, pieces.lastIndex))

        var count = (text.length + budget - 1) / budget
        var starts: List<Int>? = null
        while (starts == null && count <= pieces.size) starts = place(text.length, pieces, count++, budget)
        val bounds = (starts ?: greedy(pieces, budget)) + pieces.size
        return (0 until bounds.lastIndex).map { i -> part(i + 1, text, pieces, bounds[i], bounds[i + 1] - 1) }
    }

    /** Never needed in practice: every piece fits, so packing them in order always works. */
    private fun greedy(pieces: List<Piece>, budget: Int): List<Int> {
        val starts = mutableListOf(0)
        for (k in 1 until pieces.size) if (pieces[k].end - pieces[starts.last()].start > budget) starts += k
        return starts
    }

    /** The piece index each part starts at, or null when the last part would go over the budget. */
    private fun place(total: Int, pieces: List<Piece>, count: Int, budget: Int): List<Int>? {
        val starts = mutableListOf(0)
        val window = total.toDouble() / count * WINDOW
        for (i in 1 until count) {
            val from = pieces[starts.last()].start
            val ideal = total.toDouble() * i / count
            // A cut before piece k ends the part at piece k - 1; it must fit, and leave no more than the parts still to come can hold.
            val fitting = (starts.last() + 1 until pieces.size)
                .takeWhile { pieces[it - 1].end - from <= budget }
                .filter { total - pieces[it].start <= budget.toLong() * (count - i) }
            if (fitting.isEmpty()) return null
            val distance = { k: Int -> abs(pieces[k].start - ideal) }
            val near = fitting.filter { distance(it) <= window }
            val k = if (near.isNotEmpty()) {
                near.minWith(compareBy<Int> { pieces[it].rating }.thenBy(distance))
            } else {
                fitting.minBy(distance)
            }
            starts += k
        }
        return starts.takeIf { pieces.last().end - pieces[it.last()].start <= budget }
    }

    private fun part(index: Int, text: String, pieces: List<Piece>, first: Int, last: Int): Part {
        val body = text.substring(pieces[first].start, pieces[last].end)
        val pageNumbers = (first..last).mapNotNull { pieces[it].page }
        return Part(index, body, heading(body), pageNumbers.minOrNull(), pageNumbers.maxOrNull())
    }

    internal fun heading(body: String): String {
        val paragraphs = body.split("\n\n")
        paragraphs.firstOrNull { looksLikeHeading(it) }?.let { return it.trim() }
        val line = paragraphs.first().lineSequence().first().trim()
        if (line.length <= HEADING_MAX) return line
        val cut = line.lastIndexOf(' ', HEADING_MAX).takeIf { it > 0 } ?: HEADING_MAX
        return line.substring(0, cut).trimEnd() + "…"
    }

    /** Joins the pages' paragraphs with one blank line and splits them into pieces that each fit the budget. */
    private fun layout(pages: List<PdfPage>, budget: Int): Pair<String, List<Piece>> {
        val text = StringBuilder()
        val pieces = mutableListOf<Piece>()
        for (page in pages) {
            for (paragraph in page.text.split("\n\n").filter { it.isNotBlank() }) {
                if (text.isNotEmpty()) text.append("\n\n")
                val base = text.length
                val rating = if (looksLikeHeading(paragraph)) Rating.HEADING else Rating.PARAGRAPH
                text.append(paragraph)
                splitParagraph(paragraph, budget).forEachIndexed { i, (s, e, r) ->
                    pieces += Piece(base + s, base + e, page.number, if (i == 0) rating else r)
                }
            }
        }
        return text.toString() to pieces
    }

    /** Spans inside [paragraph] with the rating of the cut before each: the whole paragraph when it fits. */
    internal fun splitParagraph(paragraph: String, budget: Int): List<Triple<Int, Int, Rating>> {
        if (paragraph.length <= budget) return listOf(Triple(0, paragraph.length, Rating.PARAGRAPH))
        return sentences(paragraph).flatMapIndexed { i, (s, e) ->
            val rating = if (i == 0) Rating.PARAGRAPH else Rating.SENTENCE
            if (e - s <= budget) {
                listOf(Triple(s, e, rating))
            } else {
                hardSplit(paragraph, s, e, budget).mapIndexed { j, (a, b) -> Triple(a, b, if (j == 0) rating else Rating.HARD) }
            }
        }
    }

    /** Sentence spans, without the whitespace between them. */
    internal fun sentences(text: String): List<Pair<Int, Int>> {
        val spans = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            var end = -1
            if (c in WESTERN_END && i + 1 < text.length && text[i + 1].isWhitespace()) {
                end = i + 1
            } else if (c in CJK_END) {
                end = i + 1
                while (end < text.length && text[end] in CJK_CLOSE) end++
            }
            if (end > 0 && end < text.length) {
                spans += start to end
                var next = end
                while (next < text.length && text[next].isWhitespace()) next++
                start = next
                i = next
            } else {
                i++
            }
        }
        if (start < text.length) spans += start to text.length
        return spans
    }

    /** Splits [s, e) at the last space (or 、/，) that fits, else hard at the budget. */
    private fun hardSplit(text: String, s: Int, e: Int, budget: Int): List<Pair<Int, Int>> {
        val spans = mutableListOf<Pair<Int, Int>>()
        var pos = s
        while (e - pos > budget) {
            val limit = pos + budget
            val space = text.lastIndexOf(' ', limit).takeIf { it > pos }
            val comma = (limit - 1 downTo pos + 1).firstOrNull { text[it] == '、' || text[it] == '，' }
            when {
                space != null && (comma == null || space >= comma) -> {
                    spans += pos to space
                    pos = space + 1
                }
                comma != null -> {
                    spans += pos to comma + 1
                    pos = comma + 1
                }
                else -> {
                    spans += pos to limit
                    pos = limit
                }
            }
        }
        spans += pos to e
        return spans
    }

    private const val WINDOW = 0.25
    private const val HEADING_MAX = 80
    private const val WESTERN_END = ".!?…"
    private const val CJK_END = "。！？"
    private const val CJK_CLOSE = "」』）"
}
