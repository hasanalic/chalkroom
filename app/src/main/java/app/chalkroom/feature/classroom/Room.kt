package app.chalkroom.feature.classroom

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import app.chalkroom.students.StudentCatalog
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.ChalkroomColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the wall meets the floor, as a share of the screen height. */
const val WALL_SHARE = 0.38f

/** The bin in the corner: its centre x and width as shares of the screen width, its bottom of the height. */
const val BIN_X = 0.96f
const val BIN_W = 0.12f
const val BIN_BOTTOM = WALL_SHARE + 0.05f

/** Where the floor has faded into the page under the button. */
private const val FADE_FROM = 0.82f
private const val FADE_TO = 0.93f

/**
 * The room behind everything: wall (warmer near the floor), skirting, the shelf, clock and notes cut
 * by the screen edges, the bin in the corner, and wooden floorboards running towards the back. With
 * [shelves] the wall is lined with wooden bookshelves (the Library).
 */
@Composable
fun Room(modifier: Modifier = Modifier, shelves: Boolean = false) {
    val colors = Chalkroom.colors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val wallBottom = h * WALL_SHARE
        val dp = density

        // Wall: two flat bands, a touch warmer near the floor.
        drawRect(colors.wall, size = Size(w, wallBottom))
        drawRect(colors.wallLow, Offset(0f, wallBottom * 0.86f), Size(w, wallBottom * 0.14f))
        if (shelves) drawBookshelves(colors, wallBottom)

        drawFloor(colors, wallBottom)

        // Skirting where wall meets floor.
        val skirtH = 9f * dp
        drawRect(colors.skirting, Offset(0f, wallBottom - skirtH), Size(w, skirtH))
        drawLine(colors.ink.copy(alpha = 0.35f), Offset(0f, wallBottom - skirtH), Offset(w, wallBottom - skirtH), 1.5f * dp)
        drawLine(colors.ink.copy(alpha = 0.2f), Offset(0f, wallBottom), Offset(w, wallBottom), 1.5f * dp)

        drawShelf(colors, Offset(0f, h * 0.27f), w)
        drawClock(colors, Offset(w * 0.95f, h * 0.115f), w * 0.085f)
        drawNote(colors, colors.lavender, Offset(w * 0.875f, h * 0.215f), w * 0.09f, -6f)
        drawNote(colors, Color(0xFFFFE9A8), Offset(w * 0.955f, h * 0.255f), w * 0.09f, 8f)
        drawBin(colors, Offset(w * BIN_X, h * BIN_BOTTOM), w * BIN_W)

        // The floor fades into the page under the button, so room and UI are one surface.
        drawRect(
            Brush.verticalGradient(0f to colors.page.copy(alpha = 0f), 1f to colors.page, startY = h * FADE_FROM, endY = h * FADE_TO),
            Offset(0f, h * FADE_FROM),
            Size(w, h * (1f - FADE_FROM)),
        )
    }
}

/** Rows of books on wooden planks from the ceiling down to just above the skirting. */
private fun DrawScope.drawBookshelves(c: ChalkroomColors, wallBottom: Float) {
    val dp = density
    val w = size.width
    val rowH = wallBottom / 4.2f
    val plankH = 6f * dp
    val spines = listOf(Color(0xFF7A3B2E), Color(0xFF3F5A44), Color(0xFF8C6A2F), Color(0xFF3D4A6B), Color(0xFF6B4A3A), Color(0xFF9A7B4F))
    var row = 0
    // The first row starts low enough that the status bar stays clear.
    var shelfY = rowH * 1.3f
    while (shelfY < wallBottom * 0.9f) {
        // Books standing on this plank, varied widths and heights, a seeded gap now and then.
        var x = -((row * 13) % 17) * dp
        var i = row * 7
        while (x < w) {
            val bw = (9f + (i * 5) % 8) * dp
            val bh = rowH * (0.55f + ((i * 37) % 30) / 100f)
            if (i % 9 != 4) {
                drawRect(spines[i % spines.size], Offset(x, shelfY - bh), Size(bw, bh))
                drawLine(c.ink.copy(alpha = 0.35f), Offset(x, shelfY - bh), Offset(x, shelfY), 1f * dp)
                drawLine(c.card.copy(alpha = 0.18f), Offset(x + 2f * dp, shelfY - bh * 0.8f), Offset(x + bw - 2f * dp, shelfY - bh * 0.8f), 1.5f * dp)
            }
            x += bw + 1f * dp
            i++
        }
        drawRect(c.woodDeep, Offset(0f, shelfY), Size(w, plankH))
        drawLine(c.ink.copy(alpha = 0.35f), Offset(0f, shelfY + plankH), Offset(w, shelfY + plankH), 1.5f * dp)
        shelfY += rowH
        row++
    }
}

/** Boards in slight perspective (narrowing towards the back), three close honey tones, staggered joints. */
private fun DrawScope.drawFloor(c: ChalkroomColors, top: Float) {
    val w = size.width
    val h = size.height
    val tones = listOf(c.floor1, c.floor2, c.floor3)
    val vp = Offset(w / 2, top - h * 1.6f)
    val boards = 11
    val left = -0.35f * w
    val right = 1.35f * w
    fun xAt(bottomX: Float, y: Float): Float = vp.x + (bottomX - vp.x) * (y - vp.y) / (h - vp.y)
    val seam = c.woodDeep.copy(alpha = 0.18f)
    for (i in 0 until boards) {
        val b0 = left + (right - left) * i / boards
        val b1 = left + (right - left) * (i + 1) / boards
        // Staggered joints along each board.
        val joints = listOf(top, top + (h - top) * ((0.18f + 0.29f * i) % 0.6f + 0.12f), h)
        for (s in 0 until joints.size - 1) {
            val y0 = joints[s]
            val y1 = joints[s + 1]
            val quad = Path().apply {
                moveTo(xAt(b0, y0), y0)
                lineTo(xAt(b1, y0), y0)
                lineTo(xAt(b1, y1), y1)
                lineTo(xAt(b0, y1), y1)
                close()
            }
            drawPath(quad, tones[(i * 2 + s) % 3])
            if (s > 0) drawLine(seam, Offset(xAt(b0, y0), y0), Offset(xAt(b1, y0), y0), 1.5f * density)
        }
        drawLine(seam, Offset(xAt(b0, top), top), Offset(b0, h), 1.5f * density)
    }
}

private fun DrawScope.drawShelf(c: ChalkroomColors, at: Offset, w: Float) {
    val dp = density
    val ink = Stroke(2f * dp, join = StrokeJoin.Round)
    val plankW = w * 0.15f
    val plankH = 7f * dp
    // Books: three upright (the first cut by the edge), one leaning.
    val books = listOf(Triple(-0.012f, 0.032f, c.coral), Triple(0.02f, 0.028f, c.lavender2), Triple(0.048f, 0.03f, StudentCatalog.pip.color))
    val heights = listOf(0.11f, 0.095f, 0.105f)
    books.forEachIndexed { i, (x, bw, color) ->
        val tl = Offset(at.x + x * w, at.y - heights[i] * w)
        drawRect(color, tl, Size(bw * w, heights[i] * w))
        drawRect(c.ink, tl, Size(bw * w, heights[i] * w), style = ink)
        drawLine(c.ink.copy(alpha = 0.4f), Offset(tl.x + 0.006f * w, tl.y + 0.02f * w), Offset(tl.x + (bw - 0.006f) * w, tl.y + 0.02f * w), 1.5f * dp)
    }
    val leanBase = Offset(at.x + 0.078f * w, at.y)
    rotate(22f, leanBase) {
        val tl = Offset(leanBase.x, leanBase.y - 0.1f * w)
        drawRect(c.gold, tl, Size(0.026f * w, 0.1f * w))
        drawRect(c.ink, tl, Size(0.026f * w, 0.1f * w), style = ink)
    }
    // A small plant at the end.
    val pot = Offset(at.x + 0.125f * w, at.y)
    listOf(-30f, 5f, 35f).forEach { a ->
        rotate(a, pot) {
            val leaf = Path().apply {
                moveTo(pot.x, pot.y - 0.02f * w)
                quadraticTo(pot.x - 0.02f * w, pot.y - 0.05f * w, pot.x, pot.y - 0.075f * w)
                quadraticTo(pot.x + 0.02f * w, pot.y - 0.05f * w, pot.x, pot.y - 0.02f * w)
            }
            drawPath(leaf, Color(0xFF6BAF5B))
            drawPath(leaf, c.ink, style = Stroke(1.5f * dp, join = StrokeJoin.Round))
        }
    }
    val potPath = Path().apply {
        moveTo(pot.x - 0.022f * w, pot.y - 0.028f * w)
        lineTo(pot.x + 0.022f * w, pot.y - 0.028f * w)
        lineTo(pot.x + 0.017f * w, pot.y)
        lineTo(pot.x - 0.017f * w, pot.y)
        close()
    }
    drawPath(potPath, c.card)
    drawPath(potPath, c.ink, style = ink)
    // The plank, cut by the left edge.
    drawRect(c.woodDark, Offset(-4f * dp, at.y), Size(plankW + 4f * dp, plankH))
    drawRect(c.ink, Offset(-4f * dp, at.y), Size(plankW + 4f * dp, plankH), style = ink)
}

/** A plain clock, no numerals, at ten past ten; cut by the right edge. */
private fun DrawScope.drawClock(c: ChalkroomColors, center: Offset, r: Float) {
    val dp = density
    drawCircle(c.card, r, center)
    drawCircle(c.ink, r, center, style = Stroke(3f * dp))
    repeat(12) { i ->
        val a = (i * 30f - 90f) * PI.toFloat() / 180f
        val o = Offset(center.x + cos(a) * r * 0.82f, center.y + sin(a) * r * 0.82f)
        drawCircle(c.ink.copy(alpha = 0.5f), 1.4f * dp, o)
    }
    fun hand(clockDeg: Float, len: Float, width: Float) {
        val a = (clockDeg - 90f) * PI.toFloat() / 180f
        drawLine(c.ink, center, Offset(center.x + cos(a) * r * len, center.y + sin(a) * r * len), width, StrokeCap.Round)
    }
    hand(305f, 0.5f, 3.5f * dp) // hour: just past ten
    hand(60f, 0.72f, 2.5f * dp) // minute: ten past
    drawCircle(c.ink, 2.5f * dp, center)
}

private fun DrawScope.drawNote(c: ChalkroomColors, color: Color, center: Offset, side: Float, angle: Float) {
    val dp = density
    rotate(angle, center) {
        val tl = Offset(center.x - side / 2, center.y - side / 2)
        drawRect(color, tl, Size(side, side))
        drawRect(c.ink, tl, Size(side, side), style = Stroke(1.5f * dp))
        repeat(3) { i ->
            val y = tl.y + side * (0.38f + i * 0.18f)
            drawLine(c.ink.copy(alpha = 0.3f), Offset(tl.x + side * 0.15f, y), Offset(tl.x + side * (if (i == 2) 0.55f else 0.85f), y), 1.5f * dp)
        }
        drawCircle(c.coral, 3.5f * dp, Offset(center.x, tl.y + 3f * dp))
        drawCircle(c.ink, 3.5f * dp, Offset(center.x, tl.y + 3f * dp), style = Stroke(1.2f * dp))
    }
}

/** A wire-mesh bin with crumpled paper; one ball missed and lies on the floor. */
private fun DrawScope.drawBin(c: ChalkroomColors, bottomCenter: Offset, bw: Float) {
    val dp = density
    val ink = Stroke(2f * dp, join = StrokeJoin.Round)
    val bh = bw * 1.05f
    val topY = bottomCenter.y - bh
    val bin = Path().apply {
        moveTo(bottomCenter.x - bw / 2, topY)
        lineTo(bottomCenter.x + bw / 2, topY)
        lineTo(bottomCenter.x + bw * 0.38f, bottomCenter.y)
        lineTo(bottomCenter.x - bw * 0.38f, bottomCenter.y)
        close()
    }
    drawOval(c.ink.copy(alpha = 0.12f), Offset(bottomCenter.x - bw * 0.48f, bottomCenter.y - 4f * dp), Size(bw * 0.96f, 9f * dp))
    // Paper inside, peeking over the rim.
    listOf(-0.18f to 0.06f, 0.12f to 0.03f, -0.02f to -0.02f).forEach { (dx, dy) -> drawBall(c, Offset(bottomCenter.x + dx * bw, topY + dy * bw), bw * 0.15f) }
    drawPath(bin, c.ink2.copy(alpha = 0.18f))
    clipPath(bin) {
        val step = bw / 7f
        var x = bottomCenter.x - bw
        while (x < bottomCenter.x + bw) {
            drawLine(c.ink.copy(alpha = 0.45f), Offset(x, topY), Offset(x + bh * 0.5f, bottomCenter.y), 1.2f * dp)
            drawLine(c.ink.copy(alpha = 0.45f), Offset(x + bh * 0.5f, topY), Offset(x, bottomCenter.y), 1.2f * dp)
            x += step
        }
    }
    drawPath(bin, c.ink, style = ink)
    drawLine(c.ink, Offset(bottomCenter.x - bw / 2 - 2f * dp, topY), Offset(bottomCenter.x + bw / 2 + 2f * dp, topY), 3.5f * dp, StrokeCap.Round)
    drawBall(c, Offset(bottomCenter.x - bw * 0.85f, bottomCenter.y - bw * 0.07f), bw * 0.14f)
}

/** A crumpled paper ball; [spin] turns its creases (degrees). */
fun DrawScope.drawBall(c: ChalkroomColors, center: Offset, r: Float, spin: Float = 0f) = rotate(spin, center) {
    val dp = density
    drawCircle(c.card, r, center)
    drawCircle(c.ink, r, center, style = Stroke(1.6f * dp))
    drawLine(c.ink.copy(alpha = 0.4f), Offset(center.x - r * 0.5f, center.y - r * 0.1f), Offset(center.x + r * 0.2f, center.y + r * 0.3f), 1.2f * dp, StrokeCap.Round)
    drawLine(c.ink.copy(alpha = 0.4f), Offset(center.x + r * 0.1f, center.y - r * 0.5f), Offset(center.x + r * 0.45f, center.y + r * 0.05f), 1.2f * dp, StrokeCap.Round)
}

