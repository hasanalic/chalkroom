package app.chalkroom.feature.reading

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chalkroom.ui.theme.Chalkroom
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** One cell size for every column: the glyph's size, and the step down a stream is the glyph's height. */
private const val GLYPH_SP = 12f
private const val GLYPH_H = 0.9f
/** Columns sit side by side at one width, about this wide, 26–32 of them across a phone. */
private val COLUMN_W = 14.5.dp
private const val MIN_COLUMNS = 26
private const val MAX_COLUMNS = 32
/** Depth comes only from brightness: three tiers, faint to bright. */
private val TIER_ALPHA = floatArrayOf(0.55f, 0.8f, 1f)
private val TIER_WEIGHTS = floatArrayOf(0.35f, 0.4f, 0.25f)
/** The head is near-white and brighter than anything in its stream; the next cells are a light green. */
private val TIER_HEAD_ALPHA = floatArrayOf(0.85f, 0.95f, 1f)
private val HEAD = Color(0xFFE8FFEE)
private val NEAR = Color(0xFFA8FFC2)
private const val NEAR_CELLS = 2
/** The trail holds its green longer than a straight fade, then thins out at the top. */
private const val FADE_CURVE = 0.6f
/** The brightest the rain gets behind the student, and how far outside it that cap eases off. */
private const val FOCUS_ALPHA = 0.14f
private val FOCUS_FEATHER = 32.dp
/** Calm (count line, messages): a third of the speed, half the brightness. */
private const val CALM_SPEED = 1f / 3f
private const val CALM_DIM = 0.5f
private const val CALM_MS = 600

private class RainLayout(
    val cellW: Float,
    val cellH: Float,
    val tier: IntArray,
    val model: NotesRain,
)

/**
 * The digital rain behind the Reading screen, made of the notes' own words, on a grid of equal cells.
 * One draw pass per frame: time is only read while drawing, so the screen never recomposes for it.
 * Glyphs go straight to the native canvas through one prepared [Paint] (Skia keeps each glyph in its
 * atlas), with the colour packed per cell, so a frame allocates nothing. [focus] is the student's area
 * in root coordinates, where the rain's brightness is capped so the face and motes read first.
 */
@Composable
fun NotesRainBackground(
    text: RainText,
    highlight: Color,
    calm: Boolean,
    focus: () -> Rect,
    modifier: Modifier = Modifier,
) {
    val colors = Chalkroom.colors
    val reduced = Chalkroom.reducedMotion
    val density = LocalDensity.current
    val calmness = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(calm, reduced) {
        val target = if (calm) 1f else 0f
        if (reduced) calmness.snapTo(target) else calmness.animateTo(target, tween(CALM_MS, easing = EaseInOutSine))
    }
    val paint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = with(density) { GLYPH_SP.sp.toPx() }
            textAlign = Paint.Align.CENTER
        }
    }
    // Colours as packed RGB; the alpha is set per cell.
    val rgb = remember(colors, highlight) {
        intArrayOf(HEAD.toArgb(), NEAR.toArgb(), colors.rain.toArgb(), highlight.toArgb()).map { it and 0xFFFFFF }.toIntArray()
    }
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val layout = remember(text, width, height, reduced, density) {
            val random = Random(text.words.size * 31L + width)
            val count = (width / with(density) { COLUMN_W.toPx() }).roundToInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            val cellH = with(density) { GLYPH_SP.sp.toPx() } * GLYPH_H
            val tiers = IntArray(count) {
                val roll = random.nextFloat()
                if (roll < TIER_WEIGHTS[0]) 0 else if (roll < TIER_WEIGHTS[0] + TIER_WEIGHTS[1]) 1 else 2
            }
            val rows = ceil(height / cellH).toInt()
            RainLayout(width.toFloat() / count, cellH, tiers, NotesRain(text, count, rows, seed = random.nextLong(), frozen = reduced))
        }
        val frame = remember { mutableLongStateOf(0L) }
        LaunchedEffect(layout) {
            if (layout.model.frozen) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                        layout.model.advance(dt * (1f - calmness.value * (1f - CALM_SPEED)))
                    }
                    last = now
                    frame.longValue = now
                }
            }
        }
        val one = remember { CharArray(1) }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                frame.longValue // redraw each frame; nothing recomposes
                val dim = 1f - calmness.value * (1f - CALM_DIM)
                val area = focus()
                val feather = FOCUS_FEATHER.toPx()
                val cw = layout.cellW
                val ch = layout.cellH
                // Baseline that centres the glyph in its cell.
                val lift = (paint.ascent() + paint.descent()) / 2
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    layout.model.forEachCell { column, row, glyph, behind, fade, highlighted ->
                        val tier = layout.tier[column]
                        val cx = column * cw + cw / 2
                        val cy = row * ch + ch / 2
                        val color: Int
                        val alpha: Float
                        when {
                            behind == 0 -> { color = rgb[0]; alpha = TIER_HEAD_ALPHA[tier] }
                            highlighted -> { color = rgb[3]; alpha = fade.pow(FADE_CURVE) * minOf(1f, TIER_ALPHA[tier] + 0.2f) }
                            behind <= NEAR_CELLS -> { color = rgb[1]; alpha = TIER_ALPHA[tier] * 0.95f }
                            else -> { color = rgb[2]; alpha = fade.pow(FADE_CURVE) * TIER_ALPHA[tier] * 0.9f }
                        }
                        val a = alpha * dim * focusCap(cx, cy, area, feather)
                        if (a < 0.01f) return@forEachCell
                        paint.color = ((a * 255f).toInt() shl 24) or color
                        one[0] = glyph
                        native.drawText(one, 0, 1, cx, cy - lift, paint)
                    }
                }
            },
        )
    }
}

/** 1 away from the student, [FOCUS_ALPHA] over them, eased across [feather]. */
private fun focusCap(x: Float, y: Float, area: Rect, feather: Float): Float {
    if (area.isEmpty) return 1f
    val dx = maxOf(area.left - x, 0f, x - area.right)
    val dy = maxOf(area.top - y, 0f, y - area.bottom)
    val dist = maxOf(dx, dy)
    return FOCUS_ALPHA + (1f - FOCUS_ALPHA) * (dist / feather).coerceIn(0f, 1f)
}
