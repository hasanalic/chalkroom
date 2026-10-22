package app.chalkroom.feature.classroom.room

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.chalkroom.ui.theme.ChalkroomColors

/**
 * The curated colours behind every room token. Each layer is the app palette with only its own
 * tokens swapped, so Room, Desk and the chalk draw unchanged; Classic gives back the palette as is.
 */
object RoomPalette {
    private val base = ChalkroomColors()

    fun board(b: BoardColor): Color = when (b) {
        BoardColor.GREEN -> base.board
        BoardColor.BLACK -> Color(0xFF25292D)
        BoardColor.SLATE_BLUE -> Color(0xFF33405A)
        BoardColor.BURGUNDY -> Color(0xFF572631)
        BoardColor.DARK_GREEN -> Color(0xFF2A4133)
        BoardColor.BLUE_GREY -> Color(0xFF3E4957)
        BoardColor.DEEP_NAVY -> Color(0xFF19213B)
    }

    /** wall, wallLow, skirting. */
    fun wall(w: WallColor): Triple<Color, Color, Color> = when (w) {
        WallColor.CREAM -> Triple(base.wall, base.wallLow, base.skirting)
        WallColor.NAVY -> Triple(Color(0xFF34406A), Color(0xFF2D3860), Color(0xFF222B4A))
        WallColor.SHELVES -> Triple(Color(0xFFC48E57), Color(0xFFB4804C), Color(0xFF8A5A2E))
        WallColor.SUNSET_GLOW -> Triple(Color(0xFFFFD2A0), Color(0xFFFFBE88), Color(0xFFE59C66))
        WallColor.STEEL -> Triple(Color(0xFFB9C2CC), Color(0xFFAAB4BF), Color(0xFF808A96))
        WallColor.SAGE -> Triple(Color(0xFFDCE6D2), Color(0xFFCEDBC2), Color(0xFFA7BA97))
        WallColor.SPACE -> Triple(Color(0xFF151B36), Color(0xFF11162E), Color(0xFF3A4458))
    }

    /** floor1, floor2, floor3 and the seam tone. */
    private fun floor(f: Floor): List<Color> = when (f) {
        Floor.WOOD -> listOf(base.floor1, base.floor2, base.floor3, base.woodDeep)
        Floor.DARK -> listOf(Color(0xFF7C5839), Color(0xFF704E31), Color(0xFF876241), Color(0xFF3B2716))
        Floor.METAL -> listOf(Color(0xFF3B4352), Color(0xFF353D4B), Color(0xFF414A5A), Color(0xFF1A1F29))
    }

    /** Desk top, front lip, panel and legs. */
    fun desk(d: DeskStyle): Triple<Color, Color, Color> = when (d) {
        DeskStyle.WOOD -> Triple(base.wood, base.woodDark, base.woodDeep)
        DeskStyle.PAINTED -> Triple(Color(0xFF86B6D8), Color(0xFF6A9CC2), Color(0xFF5083A9))
        DeskStyle.METAL -> Triple(Color(0xFFC6CDD5), Color(0xFFA8B1BB), Color(0xFF8A949F))
    }

    /** The wash the light lays over the room; transparent for plain afternoon light. */
    fun light(l: LightColor): Color = when (l) {
        LightColor.MORNING -> Color(0xFFE8F2FF).copy(alpha = 0.12f)
        LightColor.AFTERNOON -> Color.Transparent
        LightColor.EVENING -> Color(0xFF1B2350).copy(alpha = 0.22f)
        LightColor.GOLDEN -> Color(0xFFFFC36A).copy(alpha = 0.13f)
        LightColor.SUNSET -> Color(0xFFFF8A4E).copy(alpha = 0.14f)
        LightColor.COOL_BLUE -> Color(0xFF38D9F0).copy(alpha = 0.13f)
    }

    /** A wall dark enough that the status bar icons go light over it. */
    fun darkWall(w: WallColor): Boolean = wall(w).first.luminance() < 0.3f

    /** Space Lab's porthole: the metal rim, deep space through the glass, and the little planet. */
    val portholeRim = Color(0xFF8C97A8)
    val space = Color(0xFF070B1E)
    val planet = Color(0xFFF08A5D)

    /** The warm pool of light under a desk lamp. */
    val lampGlow = Color(0xFFFFD58A)

    fun roomColors(c: RoomConfig): ChalkroomColors {
        val (wall, wallLow, skirting) = wall(c.wall)
        val (f1, f2, f3, seam) = floor(c.floor)
        return base.copy(wall = wall, wallLow = wallLow, skirting = skirting, floor1 = f1, floor2 = f2, floor3 = f3, woodDeep = seam)
    }

    fun deskColors(c: RoomConfig): ChalkroomColors {
        val (top, front, panel) = desk(c.desks)
        return base.copy(wood = top, woodDark = front, woodDeep = panel)
    }

    fun boardColors(board: Color): ChalkroomColors = base.copy(board = board)
}
