package app.chalkroom.feature.classroom.room

/**
 * How the classroom is dressed. Every value is a curated token; the colours behind them live in
 * RoomPalette. Presets may use shades that aren't swatches in the sheet ([swatch] false): the sheet
 * then shows the preset's nearest swatch as picked.
 */
enum class BoardColor(val label: String, val swatch: Boolean, val stars: Boolean = false) {
    GREEN("Green", true),
    BLACK("Black", true),
    SLATE_BLUE("Slate blue", true),
    BURGUNDY("Burgundy", true),
    DARK_GREEN("Dark green", false),
    BLUE_GREY("Blue-grey", false),
    DEEP_NAVY("Deep navy", false, stars = true),
}

/**
 * The first six are the sheet's swatches. [SHELVES] is a wall of wooden shelves, not just a colour;
 * [SPACE] (Space Lab's only) is a deep space-navy wall with a porthole onto the stars.
 */
enum class WallColor(val label: String, val swatch: Boolean = true) {
    CREAM("Cream"),
    NAVY("Navy"),
    SHELVES("Shelves"),
    SUNSET_GLOW("Sunset"),
    STEEL("Steel"),
    SAGE("Sage"),
    SPACE("Space", swatch = false),
}

enum class DeskStyle(val label: String) {
    WOOD("Wood"),
    PAINTED("Painted"),
    METAL("Metal"),
}

/** Only presets pick the floor. [METAL] is dark metal plates, not planks. */
enum class Floor { WOOD, DARK, METAL }

/** The first three are the sheet's choices; the rest belong to presets. Only [EVENING] turns the desk lamps on. */
enum class LightColor(val label: String, val swatch: Boolean) {
    MORNING("Morning", true),
    AFTERNOON("Afternoon", true),
    EVENING("Evening", true),
    GOLDEN("Golden", false),
    SUNSET("Sunset", false),
    COOL_BLUE("Cool cyan", false),
    ;

    val lampsOn: Boolean get() = this == EVENING
}

/** The four values the sheet can fine-tune. */
data class RoomValues(val board: BoardColor, val wall: WallColor, val desks: DeskStyle, val light: LightColor)

enum class RoomPreset(
    val label: String,
    val values: RoomValues,
    val floor: Floor,
    /** The swatch the sheet shows as picked while the preset's own (non-swatch) shade is up. */
    val nearestBoard: BoardColor = values.board,
    val nearestLight: LightColor = values.light,
    val nearestWall: WallColor = values.wall,
) {
    CLASSIC("Classic", RoomValues(BoardColor.GREEN, WallColor.CREAM, DeskStyle.WOOD, LightColor.AFTERNOON), Floor.WOOD),
    NIGHT_STUDY("Night Study", RoomValues(BoardColor.BLACK, WallColor.NAVY, DeskStyle.PAINTED, LightColor.EVENING), Floor.DARK),
    LIBRARY(
        "Library",
        RoomValues(BoardColor.DARK_GREEN, WallColor.SHELVES, DeskStyle.WOOD, LightColor.GOLDEN),
        Floor.WOOD,
        nearestBoard = BoardColor.GREEN,
        nearestLight = LightColor.AFTERNOON,
    ),
    SUNSET(
        "Sunset",
        RoomValues(BoardColor.BLUE_GREY, WallColor.SUNSET_GLOW, DeskStyle.PAINTED, LightColor.SUNSET),
        Floor.WOOD,
        nearestBoard = BoardColor.SLATE_BLUE,
        nearestLight = LightColor.AFTERNOON,
    ),
    SPACE_LAB(
        "Space Lab",
        RoomValues(BoardColor.DEEP_NAVY, WallColor.SPACE, DeskStyle.METAL, LightColor.COOL_BLUE),
        Floor.METAL,
        nearestBoard = BoardColor.SLATE_BLUE,
        nearestLight = LightColor.MORNING,
        nearestWall = WallColor.NAVY,
    ),
}

/** The whole room as last left. [preset] is the last one picked; the floor always comes from it. */
data class RoomConfig(
    val preset: RoomPreset = RoomPreset.CLASSIC,
    val values: RoomValues = preset.values,
    val motto: String = "",
) {
    val board: BoardColor get() = values.board
    val wall: WallColor get() = values.wall
    val desks: DeskStyle get() = values.desks
    val light: LightColor get() = values.light
    val floor: Floor get() = preset.floor
}
