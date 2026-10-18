package app.chalkroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chalkroom.ui.theme.Chalkroom

private val Pill = RoundedCornerShape(50)

/** Ink-filled pill. The only primary action style in the app. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .scale(if (pressed) 0.97f else 1f)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(Pill)
            .background(Chalkroom.colors.ink)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Chalkroom.type.label.copy(color = Chalkroom.colors.paper))
    }
}

/** Outlined pill for secondary choices. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(Pill)
            .border(2.dp, Chalkroom.colors.ink, Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Chalkroom.type.label)
    }
}

/** The single card that holds what a student is saying right now. Never a chat bubble list. */
@Composable
fun LineCard(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Chalkroom.colors.paper)
            .border(2.5.dp, Chalkroom.colors.ink, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(text, style = Chalkroom.type.studentLine, textAlign = TextAlign.Start)
    }
}
