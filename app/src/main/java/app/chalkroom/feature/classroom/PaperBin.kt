package app.chalkroom.feature.classroom

import kotlin.random.Random

/**
 * Pip's paper throws at the bin, kept for the session (memory only). About one throw in four goes
 * in, never two in a row; a miss lands on the floor by the bin, and the pile there stops growing
 * at [MAX_ON_FLOOR] balls (later misses land on it). At most one throw every [GAP_MS].
 */
class PaperBin(private val random: Random = Random.Default) {
    /** Where the balls on the floor lie, as indexes into the room's floor spots, oldest first. */
    var onFloor: List<Int> = emptyList()
        private set
    private var lastIn = false
    private var lastAt = Long.MIN_VALUE / 2

    fun canThrow(now: Long): Boolean = now - lastAt >= GAP_MS

    /** One throw at [now]: whether it goes in, and where it lands when it doesn't. */
    fun toss(now: Long): Toss {
        lastAt = now
        val goesIn = !lastIn && random.nextFloat() < IN_CHANCE
        lastIn = goesIn
        if (goesIn) return Toss(goesIn = true, spot = -1)
        val spot = if (onFloor.size < MAX_ON_FLOOR) onFloor.size else random.nextInt(MAX_ON_FLOOR)
        return Toss(goesIn = false, spot = spot)
    }

    /** The missed ball has come to rest. */
    fun landed(toss: Toss) {
        if (!toss.goesIn && toss.spot !in onFloor) onFloor = onFloor + toss.spot
    }

    data class Toss(val goesIn: Boolean, val spot: Int)

    companion object {
        const val MAX_ON_FLOOR = 5
        const val GAP_MS = 20_000L
        const val IN_CHANCE = 0.25f

        /** The one bin for this process. */
        val session = PaperBin()
    }
}
