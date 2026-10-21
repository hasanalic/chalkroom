package app.chalkroom.feature.choosestudent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.chalkroom.shared.StudentId
import app.chalkroom.students.Dart
import app.chalkroom.students.Expression
import app.chalkroom.ui.theme.ChalkroomColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * What a student on the Choose screen is doing right now, all in figure widths (the student's
 * canvas is 1 wide, [FIGURE_H] tall). The body moves go through StudentFigure's own inputs or a
 * layer around it; the dust fleck, sparkles and Juno's book are drawn by this screen.
 */
@Stable
internal class Pose {
    val lift = Animatable(0f)
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    /** Lean in degrees, around the bottom centre (positive = to the right). */
    val lean = Animatable(0f)
    /** Turning round in place, degrees about the vertical axis. */
    val spin = Animatable(0f)
    val ears = Animatable(0f)
    /** Mira growing past her card while she celebrates, around her bottom (1 = her normal size). */
    val grow = Animatable(1f)
    val hat = Animatable(0f)
    var lookAt by mutableStateOf<Offset?>(null)
    var headX by mutableStateOf<Float?>(null)
    var expression by mutableStateOf(Expression.IDLE)

    // Props.
    val fleck = Animatable(Offset.Zero, Offset.VectorConverter)
    val fleckAlpha = Animatable(0f)
    /** One burst of sparkles, 0 → 1; 0 draws nothing. */
    val sparkle = Animatable(0f)
    var sparkleAt by mutableStateOf(Offset.Zero)
    /** One page turning, 0 → 1. */
    val page = Animatable(0f)

    /** How high Pip's celebration jump goes to clear the top of his box; set from layout. */
    var jumpLift by mutableFloatStateOf(1f)

    suspend fun settle(snap: Boolean) {
        lookAt = null
        headX = null
        expression = Expression.IDLE
        val rest = listOf(lift to 0f, scaleX to 1f, scaleY to 1f, lean to 0f, ears to 0f, grow to 1f, hat to 0f, fleckAlpha to 0f, page to 0f)
        sparkle.snapTo(0f)
        spin.snapTo(0f)
        if (snap) return rest.forEach { (a, v) -> a.snapTo(v) }
        coroutineScope { rest.forEach { (a, v) -> launch { a.animateTo(v, tween(220, easing = EaseInOutSine)) } } }
    }
}

/** Figure canvas height in figure widths (StudentFigure's aspect). */
internal const val FIGURE_H = 1.2f

/** Where each face's eyes sit, to aim the eyes at things drawn around them. */
private val MIRA_EYES = Offset(0.535f, 0.86f)

/** What a box was last told: celebrate (it was picked) or look at whoever was. */
internal sealed interface Cue {
    data class Celebrate(val tick: Int) : Cue
    /**
     * [dy] is -1 when the picked student's card is above, +1 below; [dx] is -1 when they stand
     * further left on their card, +1 further right, 0 on the same side.
     */
    data class LookAtPick(val dx: Int, val dy: Int, val tick: Int) : Cue
}

/** The idle loop, per student. Runs until cancelled; never in sync, each has its own clock. */
internal suspend fun Pose.idleLoop(id: StudentId) {
    delay(Random.nextLong(0, 500))
    when (id) {
        StudentId.MIRA -> miraHunt()
        StudentId.PIP -> pipHops()
        StudentId.JUNO -> junoReads()
    }
}

/** A one-shot celebration for the picked student, then back to rest. */
internal suspend fun Pose.celebrate(id: StudentId) = when (id) {
    StudentId.MIRA -> miraCelebrates()
    StudentId.PIP -> pipCelebrates()
    StudentId.JUNO -> junoCelebrates()
}

/** The other two turn to look at the picked student for a moment. */
internal suspend fun Pose.lookAtPick(dx: Int, dy: Int) {
    // Eyes down (or up) and across at them, the head bowing toward them (or lifting) and turning a touch.
    lookAt = Offset(0.05f * dx, if (dy > 0) 0.075f else -0.06f)
    headX = 0.03f * dx
    scaleY.animateTo(1f - 0.035f * dy, tween(300, easing = Dart))
    delay(1_300)
    lookAt = null
    headX = null
    scaleY.animateTo(1f, tween(400, easing = EaseInOutSine))
}

// ---- Mira: the chalk-dust hunt -----------------------------------------------------------------

/**
 * The fleck's highest point (figure widths), so the layout keeps it inside the card. It hovers
 * above Mira's head, a little to one side: her card's text sits beside her, so she hunts upward.
 */
internal const val FLECK_TOP = 0.22f

private suspend fun Pose.miraHunt() {
    var side = if (Random.nextBoolean()) 1f else -1f
    while (true) {
        // Toward her text side (left) the fleck stays nearer and she leans less.
        val toText = side < 0
        val base = Offset(0.5f + side * (if (toText) 0.22f else 0.32f), 0.34f)
        val leanIn = if (toText) 4f else 6f
        val lunge = if (toText) 7f else 10f
        fleck.snapTo(base + Offset(side * 0.06f, -0.03f))
        fleckAlpha.animateTo(1f, tween(500))
        // The fleck drifts in the air, a little nearer each moment; her eyes follow it and she leans in slowly.
        val driftMs = 3_400L + Random.nextLong(0, 900)
        coroutineScope {
            launch { lean.animateTo(side * leanIn, tween(driftMs.toInt(), easing = EaseInOutSine)) }
            launch {
                delay(driftMs / 4)
                twitch()
                delay(driftMs / 3)
                twitch()
            }
            launch {
                val start = withFrameNanos { it }
                var t = 0f
                while (t * 1000 < driftMs) {
                    t = (withFrameNanos { it } - start) / 1e9f
                    val p = Offset(
                        base.x + sin(t * 1.6f) * 0.04f - side * t * 0.02f,
                        base.y + cos(t * 1.1f) * 0.06f - 0.02f * sin(t * 2.7f),
                    )
                    fleck.snapTo(p)
                    lookAt = eyesToward(p)
                }
            }
        }
        // The lunge: up and in, ears up; the fleck slips away upward.
        val from = fleck.value
        coroutineScope {
            launch { lean.animateTo(side * lunge, tween(150, easing = Dart)) }
            launch { lift.animateTo(0.035f, tween(150, easing = Dart)) }
            launch { scaleY.animateTo(1.06f, tween(150, easing = Dart)) }
            launch { ears.animateTo(1f, tween(120, easing = Dart)) }
            launch {
                delay(70)
                fleck.animateTo(from + Offset(side * (if (toText) 0.04f else 0.12f), -0.36f), tween(420, easing = EaseOutCubic))
            }
            launch {
                delay(140)
                fleckAlpha.animateTo(0f, tween(340))
            }
            launch {
                delay(120)
                lookAt = Offset(side * 0.04f, -0.05f)
            }
        }
        delay(250)
        lookAt = null
        coroutineScope {
            launch { lean.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
            launch { lift.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
            launch { scaleY.animateTo(1f, tween(350, easing = EaseInOutSine)) }
            launch { ears.animateTo(0f, tween(500, easing = EaseInOutSine)) }
        }
        delay(Random.nextLong(700, 1_300))
        side = -side
    }
}

private suspend fun Pose.twitch() {
    ears.animateTo(0.55f, tween(80, easing = Dart))
    ears.animateTo(0f, tween(150, easing = EaseInOutSine))
    delay(90)
    ears.animateTo(0.35f, tween(70, easing = Dart))
    ears.animateTo(0f, tween(130, easing = EaseInOutSine))
}

/** Eye offset (figure widths) that points Mira's eyes at [p]; the eyes only move a little. */
private fun eyesToward(p: Offset): Offset = Offset(
    ((p.x - MIRA_EYES.x) * 0.09f).coerceIn(-0.06f, 0.06f),
    ((p.y - MIRA_EYES.y) * 0.14f).coerceIn(-0.05f, 0.04f),
)

/**
 * How big Mira grows while she celebrates, so her head and ears break out of the top of her card. Any
 * bigger and, mid-spin, her ears reach the title at 360dp.
 */
internal const val MIRA_GROW = 1.22f

private suspend fun Pose.miraCelebrates() {
    expression = Expression.RELAXED
    sparkleAt = Offset(0.53f, 0.62f)
    coroutineScope {
        launch { ears.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
        launch { grow.animateTo(MIRA_GROW, spring(dampingRatio = 0.75f, stiffness = 400f)) }
        launch {
            delay(120)
            spin.animateTo(360f, tween(700, easing = EaseInOutSine))
        }
        launch {
            delay(260)
            sparkle.animateTo(1f, tween(1_000, easing = EaseOutQuad))
        }
    }
    sparkle.snapTo(0f)
    spin.snapTo(0f)
    coroutineScope {
        launch { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        delay(700)
    }
    expression = Expression.IDLE
    ears.animateTo(0f, tween(450, easing = EaseInOutSine))
}

// ---- Pip: "pick me" hops -----------------------------------------------------------------------

/** The highest of Pip's idle hops, figure widths. */
internal const val MAX_HOP = 0.13f

private val HOPS = listOf(0.05f to 200, 0.09f to 240, MAX_HOP to 280)

private suspend fun Pose.pipHops() {
    while (true) {
        HOPS.forEachIndexed { i, (height, airMs) ->
            lookAt = Offset(0f, -0.012f * (i + 1))
            hop(height, airMs)
            delay(70)
        }
        lookAt = null
        delay(Random.nextLong(180, 420))
    }
}

/** Crouch, stretch on take-off, rise, fall, squash on landing, recover. */
private suspend fun Pose.hop(height: Float, airMs: Int, crouch: Float = 0.1f, stretch: Float = 0.12f) {
    coroutineScope {
        launch { scaleX.animateTo(1f + crouch * 0.8f, tween(90, easing = EaseOutQuad)) }
        scaleY.animateTo(1f - crouch, tween(90, easing = EaseOutQuad))
    }
    coroutineScope {
        launch { lift.animateTo(height, tween(airMs, easing = EaseOutCubic)) }
        launch {
            scaleX.animateTo(1f - stretch * 0.6f, tween(100, easing = Dart))
            scaleX.animateTo(1f, tween(airMs - 100, easing = EaseInOutSine))
        }
        scaleY.animateTo(1f + stretch, tween(100, easing = Dart))
        scaleY.animateTo(1f, tween(airMs - 100, easing = EaseInOutSine))
    }
    val fallMs = (airMs * 0.85f).toInt()
    coroutineScope {
        launch { scaleY.animateTo(1f + stretch * 0.4f, tween(fallMs, easing = EaseInQuad)) }
        lift.animateTo(0f, tween(fallMs, easing = EaseInQuad))
    }
    coroutineScope {
        launch { scaleX.animateTo(1f + crouch * 1.2f, tween(70, easing = Dart)) }
        scaleY.animateTo(1f - crouch * 1.4f, tween(70, easing = Dart))
    }
    coroutineScope {
        launch { scaleX.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f)) }
        scaleY.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
    }
}

private suspend fun Pose.pipCelebrates() {
    expression = Expression.RELAXED
    // A deep crouch, then a big jump that clears the top of his box.
    coroutineScope {
        launch { scaleX.animateTo(1.16f, tween(150, easing = EaseOutQuad)) }
        scaleY.animateTo(0.8f, tween(150, easing = EaseOutQuad))
    }
    val top = jumpLift
    coroutineScope {
        launch { lift.animateTo(top, tween(430, easing = EaseOutCubic)) }
        launch {
            scaleX.animateTo(0.84f, tween(120, easing = Dart))
            scaleX.animateTo(1f, tween(310, easing = EaseInOutSine))
        }
        launch {
            delay(300)
            sparkleAt = Offset(0.6f, 0.62f - top)
            sparkle.snapTo(0f)
            sparkle.animateTo(1f, tween(800, easing = EaseOutQuad))
            sparkle.snapTo(0f)
        }
        scaleY.animateTo(1.25f, tween(120, easing = Dart))
        scaleY.animateTo(1f, tween(310, easing = EaseInOutSine))
        // Squashy, bouncy landing.
        coroutineScope {
            launch { scaleY.animateTo(1.12f, tween(380, easing = EaseInCubic)) }
            lift.animateTo(0f, tween(380, easing = EaseInCubic))
        }
        coroutineScope {
            launch { scaleX.animateTo(1.28f, tween(80, easing = Dart)) }
            scaleY.animateTo(0.7f, tween(80, easing = Dart))
        }
        coroutineScope {
            launch { scaleX.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 420f)) }
            scaleY.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 420f))
        }
    }
    hop(0.05f, 170, crouch = 0.06f, stretch = 0.06f)
    delay(500)
    expression = Expression.IDLE
    delay(300)
}

// ---- Juno: slow reading ------------------------------------------------------------------------

private suspend fun Pose.junoReads() {
    while (true) {
        repeat(if (Random.nextFloat() < 0.7f) 2 else 3) { line ->
            val y = 0.035f + line * 0.012f
            var x = -0.035f
            var reread = Random.nextFloat() < 0.4f
            look(x, y)
            delay(600)
            while (x < 0.035f) {
                delay(Random.nextLong(420, 640))
                x += 0.0125f
                look(x, y)
                if (reread && x > 0f && Random.nextFloat() < 0.6f) {
                    // Back to the start of the line to read it again, with a small thoughtful tilt.
                    reread = false
                    delay(450)
                    x = -0.035f
                    look(x, y)
                    lean.animateTo(-2.5f, tween(500, easing = EaseInOutSine))
                    delay(800)
                    lean.animateTo(0f, tween(700, easing = EaseInOutSine))
                }
            }
            delay(350)
        }
        // Turn the page, then back to the top-left of the new one.
        look(0.03f, 0.06f)
        delay(250)
        page.snapTo(0f)
        page.animateTo(1f, tween(750, easing = EaseInOutSine))
        page.snapTo(0f)
        delay(400)
    }
}

private fun Pose.look(x: Float, y: Float) {
    lookAt = Offset(x, y)
    headX = x * 0.3f
}

private suspend fun Pose.junoCelebrates() {
    // Surprise: eyes wide and the hat pops up off her head, then lands back.
    expression = Expression.GOT_IT
    lookAt = null
    headX = null
    hat.animateTo(0.34f, tween(220, easing = EaseOutCubic))
    delay(110)
    hat.animateTo(0f, tween(260, easing = EaseInCubic))
    coroutineScope {
        launch {
            scaleY.animateTo(0.95f, tween(70, easing = Dart))
            scaleY.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
        launch {
            hat.animateTo(0.03f, tween(90, easing = EaseOutQuad))
            hat.animateTo(0f, tween(110, easing = EaseInQuad))
        }
    }
    delay(380)
    // Then a slow, happy sway.
    expression = Expression.RELAXED
    for (to in listOf(4f, -4f, 3f, 0f)) lean.animateTo(to, tween(620, easing = EaseInOutSine))
    delay(300)
    expression = Expression.IDLE
}

// ---- Props drawn by the screen -----------------------------------------------------------------

/** A fleck of chalk dust: a cream speck with a thin ink rim and two tiny crumbs. */
internal fun DrawScope.drawFleck(at: Offset, w: Float, alpha: Float, colors: ChalkroomColors) {
    if (alpha <= 0f) return
    val c = at * w
    drawCircle(colors.page.copy(alpha = alpha), 0.036f * w, c)
    drawCircle(colors.ink.copy(alpha = alpha), 0.036f * w, c, style = Stroke(0.01f * w))
    drawCircle(colors.ink.copy(alpha = 0.55f * alpha), 0.009f * w, c + Offset(0.06f, -0.035f) * w)
    drawCircle(colors.ink.copy(alpha = 0.55f * alpha), 0.007f * w, c + Offset(-0.05f, 0.045f) * w)
}

/** A burst of small four-point sparkles around [at], growing out and fading as [p] runs 0 → 1. */
internal fun DrawScope.drawSparkles(at: Offset, p: Float, w: Float, fills: List<Color>, ink: Color, spread: Float) {
    if (p <= 0f || p >= 1f) return
    val n = 6
    repeat(n) { i ->
        val a = (i / n.toFloat()) * 2f * PI.toFloat() + 0.4f
        val r = (0.36f + 0.12f * (i % 2)) * spread * (0.55f + 0.45f * EaseOutCubic.transform(p))
        val center = (at + Offset(cos(a) * r, sin(a) * r * 0.8f)) * w
        val size = 0.075f * w * sin(PI.toFloat() * p) * (if (i % 2 == 0) 1f else 0.7f)
        val star = Path().apply {
            moveTo(center.x, center.y - size)
            quadraticTo(center.x, center.y, center.x + size, center.y)
            quadraticTo(center.x, center.y, center.x, center.y + size)
            quadraticTo(center.x, center.y, center.x - size, center.y)
            quadraticTo(center.x, center.y, center.x, center.y - size)
            close()
        }
        drawPath(star, fills[i % fills.size])
        drawPath(star, ink, style = Stroke(0.012f * w))
    }
}

/**
 * Juno's small open book, held low in front of her: two cream pages with faint lines on a dark
 * cover. [page] 0 → 1 turns the right page over onto the left.
 */
internal fun DrawScope.drawBook(w: Float, page: Float, colors: ChalkroomColors) {
    val top = 1.0f * w
    val h = 0.17f * w
    val spine = 0.5f * w
    val pageW = 0.27f * w
    // Cover, a little bigger than the pages.
    drawRoundRect(colors.ink, Offset(spine - pageW - 0.025f * w, top - 0.012f * w), Size(2 * pageW + 0.05f * w, h + 0.03f * w), CornerRadius(0.02f * w))
    listOf(-1f, 1f).forEach { side ->
        val left = if (side < 0) spine - pageW else spine
        drawRoundRect(colors.page, Offset(left, top), Size(pageW, h), CornerRadius(0.012f * w))
        repeat(4) { i ->
            val y = top + h * (0.24f + 0.19f * i)
            val end = if (i == 3) 0.55f else 0.84f
            drawLine(colors.ink.copy(alpha = 0.3f), Offset(left + pageW * 0.14f, y), Offset(left + pageW * end, y), 0.008f * w)
        }
    }
    drawLine(colors.ink, Offset(spine, top), Offset(spine, top + h), 0.008f * w)
    // The turning page: its free edge swings from the right side over to the left.
    if (page in 0.001f..0.999f) {
        val reach = cos(PI.toFloat() * page) * pageW
        val lift = sin(PI.toFloat() * page) * 0.03f * w
        val edge = spine + reach
        val turning = Path().apply {
            moveTo(spine, top)
            lineTo(edge, top - lift)
            lineTo(edge, top + h - lift)
            lineTo(spine, top + h)
            close()
        }
        drawPath(turning, colors.page)
        drawPath(turning, colors.ink, style = Stroke(0.007f * w))
        if (abs(reach) > 0.02f * w) {
            drawLine(colors.ink.copy(alpha = 0.2f), Offset(spine + reach * 0.16f, top + h * 0.35f), Offset(spine + reach * 0.8f, top + h * 0.35f - lift), 0.008f * w)
        }
    }
    drawRoundRect(colors.ink, Offset(spine - pageW, top), Size(2 * pageW, h), CornerRadius(0.012f * w), style = Stroke(0.008f * w))
}
