package app.chalkroom.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chalkroom.R

/** Palette from design-brief.md §0. No red, no warning colors anywhere. */
@Immutable
data class ChalkroomColors(
    val page: Color = Color(0xFFFFFDF5),
    val card: Color = Color(0xFFFFFFFF),
    val ink: Color = Color(0xFF2B364B),
    val ink2: Color = Color(0xFF3A4560),
    val gold: Color = Color(0xFFF5C522),
    val goldLedge: Color = Color(0xFFC68800),
    val coral: Color = Color(0xFFF59B74),
    val coralLedge: Color = Color(0xFFC85B35),
    val lavender: Color = Color(0xFFEAE6F8),
    val lavender2: Color = Color(0xFFB9B7E8),
    val muted: Color = Color(0xFF6E7278),
    val line: Color = Color(0xFFE7E2D3),
    val disabled: Color = Color(0xFFEDEAE0),
    // Room (classroom) colors.
    val wall: Color = Color(0xFFFFF5E2),
    val wallLow: Color = Color(0xFFFCEBD0),
    val skirting: Color = Color(0xFFEBD3AE),
    val floor1: Color = Color(0xFFEDBE7A),
    val floor2: Color = Color(0xFFE6B26B),
    val floor3: Color = Color(0xFFF1C88A),
    val wood: Color = Color(0xFFEDB65A),
    val woodDark: Color = Color(0xFFD39740),
    val woodDeep: Color = Color(0xFFB9792C),
    val board: Color = Color(0xFF3E5149),
    val chair: Color = Color(0xFF5B6680),
    // Lights off: the deep navy wash over the dark room, and the switch's warm glow.
    val night: Color = Color(0xFF0A1230),
    val switchGlow: Color = Color(0xFFFFCF7A),
    // Juno's cream face and the shadow its hat brim casts on it.
    val junoFace: Color = Color(0xFFF5EDE2),
    val junoShadow: Color = Color(0xFF6B2A16),
) {
    // Older names kept so not-yet-restyled screens follow the new palette.
    val paper: Color get() = page
    val paperDeep: Color get() = line
    val chalk: Color get() = card
    val inkSoft: Color get() = muted
}

@Immutable
data class ChalkroomType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val button: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    /** Bold body-size label. */
    val label: TextStyle,
    /** The student's line on the dark speech card. */
    val studentLine: TextStyle,
    /** The user's own quoted words. */
    val quote: TextStyle,
    /** White text on the board. */
    val chalk: TextStyle,
    /** Chalk handwriting: what you taught, written on the board. */
    val chalkHand: TextStyle,
)

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private val Nunito = FontFamily(nunito(400), nunito(700), nunito(800))

@OptIn(ExperimentalTextApi::class)
private val Caveat = FontFamily(
    Font(R.font.caveat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

@OptIn(ExperimentalTextApi::class)
private val LoraItalic = FontFamily(
    Font(R.font.lora_italic, FontWeight.Medium, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

private fun type(colors: ChalkroomColors) = ChalkroomType(
    display = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, color = colors.ink),
    headline = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, color = colors.ink),
    title = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 25.sp, color = colors.ink),
    button = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = colors.ink),
    body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = colors.ink),
    small = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted),
    label = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.ink),
    studentLine = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 28.sp, color = colors.card),
    quote = TextStyle(fontFamily = LoraItalic, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 22.sp, color = colors.ink),
    chalk = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = colors.card),
    chalkHand = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 25.sp, color = colors.card.copy(alpha = 0.93f)),
)

/** Radii from the brief. */
object Radii {
    val button = 20.dp
    val card = 24.dp
}

val LocalChalkroomColors = staticCompositionLocalOf { ChalkroomColors() }
val LocalChalkroomType = staticCompositionLocalOf { type(ChalkroomColors()) }

/** True when the system "remove animations" setting is on: no idle loops, motes still. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object Chalkroom {
    val colors: ChalkroomColors @Composable get() = LocalChalkroomColors.current
    val type: ChalkroomType @Composable get() = LocalChalkroomType.current
    val reducedMotion: Boolean @Composable get() = LocalReducedMotion.current
}

@Composable
fun ChalkroomTheme(content: @Composable () -> Unit) {
    val colors = ChalkroomColors()
    val context = LocalContext.current
    val reduced = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    // Material is only a base (text fields, ripples); its colors are mapped onto the palette.
    val scheme = lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.page,
        background = colors.page,
        onBackground = colors.ink,
        surface = colors.page,
        onSurface = colors.ink,
        surfaceVariant = colors.lavender,
        onSurfaceVariant = colors.muted,
        outline = colors.ink,
        error = colors.ink,
        onError = colors.page,
    )
    CompositionLocalProvider(
        LocalChalkroomColors provides colors,
        LocalChalkroomType provides type(colors),
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
