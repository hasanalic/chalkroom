package app.chalkroom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette from spec.md > Look and Feel. No red, orange-red or warning colors anywhere in teaching. */
@Immutable
data class ChalkroomColors(
    val paper: Color = Color(0xFFF4EBD9),
    val paperDeep: Color = Color(0xFFE9DCC2),
    val wood: Color = Color(0xFFB9875A),
    val woodDark: Color = Color(0xFF8E6440),
    val board: Color = Color(0xFF3F4A3C),
    val chalk: Color = Color(0xFFEDE8DA),
    val ink: Color = Color(0xFF2A2420),
    val inkSoft: Color = Color(0xFF2A2420).copy(alpha = 0.6f),
    val mira: Color = Color(0xFFE2B04A),
    val pip: Color = Color(0xFF4FA3A0),
    val juno: Color = Color(0xFF9C8CC4),
    val junoFace: Color = Color(0xFFF7F1E3),
    val mote: Color = Color(0xFFD8D2C4).copy(alpha = 0.7f),
)

@Immutable
data class ChalkroomType(
    val title: TextStyle,
    val body: TextStyle,
    val studentLine: TextStyle,
    val label: TextStyle,
    val chalk: TextStyle,
)

// Agent suggestion: a rounded bookish face (e.g. Nunito). System sans stands in for now.
private val Family = FontFamily.SansSerif

private fun type(colors: ChalkroomColors) = ChalkroomType(
    title = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = colors.ink),
    body = TextStyle(fontFamily = Family, fontSize = 16.sp, lineHeight = 23.sp, color = colors.ink),
    studentLine = TextStyle(fontFamily = Family, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 27.sp, color = colors.ink),
    label = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink),
    chalk = TextStyle(fontFamily = Family, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, color = colors.chalk),
)

val LocalChalkroomColors = staticCompositionLocalOf { ChalkroomColors() }
val LocalChalkroomType = staticCompositionLocalOf { type(ChalkroomColors()) }

object Chalkroom {
    val colors: ChalkroomColors @Composable get() = LocalChalkroomColors.current
    val type: ChalkroomType @Composable get() = LocalChalkroomType.current
}

@Composable
fun ChalkroomTheme(content: @Composable () -> Unit) {
    val colors = ChalkroomColors()
    // Material is only a base (text fields, ripples); its colors are mapped onto the palette.
    val scheme = lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.paper,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.paper,
        onSurface = colors.ink,
        surfaceVariant = colors.paperDeep,
        onSurfaceVariant = colors.inkSoft,
        outline = colors.ink,
        error = colors.ink,
        onError = colors.paper,
    )
    CompositionLocalProvider(LocalChalkroomColors provides colors, LocalChalkroomType provides type(colors)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
