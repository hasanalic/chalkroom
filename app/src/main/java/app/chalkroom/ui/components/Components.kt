package app.chalkroom.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chalkroom.ui.theme.Chalkroom
import app.chalkroom.ui.theme.Radii

val ButtonShape = RoundedCornerShape(Radii.button)
val CardShape = RoundedCornerShape(Radii.card)

const val BUTTON_LEDGE_DP = 4
const val CARD_LEDGE_DP = 3

/**
 * The app's one depth device: [content]'s surface sits on a flat extrusion of [ledgeColor], same
 * [shape], [depth] below it. When [pressed], the surface moves down onto its ledge.
 */
@Composable
fun Ledge(
    shape: Shape,
    color: Color,
    ledgeColor: Color,
    modifier: Modifier = Modifier,
    depth: Dp = CARD_LEDGE_DP.dp,
    pressed: Boolean = false,
    border: Color? = null,
    borderWidth: Dp = 2.dp,
    contentModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val press by animateDpAsState(
        if (pressed) depth else 0.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh),
        label = "press",
    )
    // propagateMinConstraints: a fillMaxWidth on [modifier] stretches the surface too.
    Box(modifier.padding(bottom = depth), propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().offset(y = depth).clip(shape).background(ledgeColor))
        Box(
            Modifier
                .offset(y = press)
                .clip(shape)
                .background(color)
                .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier)
                .then(contentModifier),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** Shared body of every ledge button. */
@Composable
private fun LedgeButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    ledgeColor: Color,
    textColor: Color,
    modifier: Modifier,
    enabled: Boolean,
    border: Color? = null,
    small: Boolean = false,
) {
    val colors = Chalkroom.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Ledge(
        shape = ButtonShape,
        color = if (enabled) color else colors.disabled,
        ledgeColor = if (enabled) ledgeColor else colors.line,
        depth = BUTTON_LEDGE_DP.dp,
        pressed = pressed && enabled,
        border = if (enabled) border else null,
        modifier = modifier,
        contentModifier = Modifier
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 20.dp, vertical = if (small) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = (if (small) Chalkroom.type.label else Chalkroom.type.button).copy(color = if (enabled) textColor else colors.muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Gold, on a deeper gold ledge. The primary action everywhere. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false) {
    val c = Chalkroom.colors
    LedgeButton(text, onClick, c.gold, c.goldLedge, c.ink, modifier, enabled, small = small)
}

/** Coral, for a second, warmer choice. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Chalkroom.colors
    LedgeButton(text, onClick, c.coral, c.coralLedge, c.ink, modifier, enabled)
}

/** White with an ink outline, on an ink ledge. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false) {
    val c = Chalkroom.colors
    LedgeButton(text, onClick, c.card, c.ink, c.ink, modifier, enabled, border = c.ink, small = small)
}

/** Just words, no surface: the quietest action, for a way out or a second way in. */
@Composable
fun TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(ButtonShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Chalkroom.type.label.copy(color = Chalkroom.colors.ink, textDecoration = TextDecoration.Underline), textAlign = TextAlign.Center)
    }
}

/** A light card on a lavender ledge. */
@Composable
fun LightCard(
    modifier: Modifier = Modifier,
    ledgeColor: Color = Chalkroom.colors.lavender2,
    border: Color? = null,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Ledge(
        shape = CardShape,
        color = Chalkroom.colors.card,
        ledgeColor = ledgeColor,
        modifier = modifier,
        border = border,
        borderWidth = 1.5.dp,
        contentModifier = contentModifier.padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}

private val NOTCH_W = 22.dp
private val NOTCH_H = 11.dp

/**
 * The single card that holds what a student is saying right now: dark, with a notch on top
 * pointing up at the student, on a lavender ledge. Never a chat bubble list.
 */
@Composable
fun LineCard(text: String, modifier: Modifier = Modifier, content: (@Composable BoxScope.() -> Unit)? = null) {
    val colors = Chalkroom.colors
    Box(
        modifier
            .padding(top = NOTCH_H)
            .drawBehind {
                val w = NOTCH_W.toPx()
                val h = NOTCH_H.toPx()
                val cx = size.width / 2
                drawPath(
                    Path().apply {
                        moveTo(cx - w / 2, 1f)
                        lineTo(cx, -h)
                        lineTo(cx + w / 2, 1f)
                        close()
                    },
                    colors.ink,
                )
            },
    ) {
        Ledge(
            shape = CardShape,
            color = colors.ink,
            ledgeColor = colors.lavender2,
            modifier = Modifier.fillMaxWidth(),
            contentModifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (content != null) content() else Text(text, style = Chalkroom.type.studentLine)
        }
    }
}
