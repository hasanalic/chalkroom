package app.chalkroom.feature.classroom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PaperBinTest {
    @Test
    fun `about one throw in four goes in, never two in a row`() {
        val bin = PaperBin(Random(7))
        var now = 0L
        var last = false
        var ins = 0
        repeat(2_000) {
            val toss = bin.toss(now)
            assertFalse(last && toss.goesIn)
            last = toss.goesIn
            if (toss.goesIn) ins++
            bin.landed(toss)
            now += PaperBin.GAP_MS
        }
        // 1/4 of the throws that follow a miss: about 20% overall.
        assertTrue(ins in 300..500, "ins=$ins")
    }

    @Test
    fun `misses pile up on the floor, at most five`() {
        val bin = PaperBin(Random(1))
        var now = 0L
        repeat(40) {
            bin.landed(bin.toss(now))
            now += PaperBin.GAP_MS
        }
        assertEquals(PaperBin.MAX_ON_FLOOR, bin.onFloor.size)
        assertEquals((0 until PaperBin.MAX_ON_FLOOR).toSet(), bin.onFloor.toSet())
    }

    @Test
    fun `at most one throw every 20 seconds`() {
        val bin = PaperBin(Random(3))
        assertTrue(bin.canThrow(1_000L))
        bin.toss(1_000L)
        assertFalse(bin.canThrow(1_000L + PaperBin.GAP_MS - 1))
        assertTrue(bin.canThrow(1_000L + PaperBin.GAP_MS))
    }
}
