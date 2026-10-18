package app.chalkroom.server.ai

import app.chalkroom.server.ai.MisconceptionGenerator.Companion.selectMisconceptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectMisconceptionsTest {

    private fun m(flaw: ThinkingFlaw, n: Int) = GeneratedMisconception(flaw.name, "s$n", "r$n")

    @Test
    fun `keeps only own-flaw items when there are at least three`() {
        val items = listOf(m(ThinkingFlaw.OMISSION, 1), m(ThinkingFlaw.CONFUSION, 2), m(ThinkingFlaw.OMISSION, 3), m(ThinkingFlaw.OMISSION, 4))
        assertEquals(listOf("s1", "s3", "s4"), selectMisconceptions(items, ThinkingFlaw.OMISSION).map { it.statement })
    }

    @Test
    fun `tops up with other items in original order when the filter leaves fewer than three`() {
        val items = listOf(m(ThinkingFlaw.CONFUSION, 1), m(ThinkingFlaw.OMISSION, 2), m(ThinkingFlaw.REVERSAL, 3), m(ThinkingFlaw.CONFUSION, 4))
        assertEquals(listOf("s1", "s2", "s3"), selectMisconceptions(items, ThinkingFlaw.OMISSION).map { it.statement })
    }

    @Test
    fun `never returns an empty deck just because no item matched the flaw`() {
        val items = listOf(m(ThinkingFlaw.CONFUSION, 1), m(ThinkingFlaw.REVERSAL, 2), m(ThinkingFlaw.CONFUSION, 3))
        assertEquals(3, selectMisconceptions(items, ThinkingFlaw.OMISSION).size)
    }

    @Test
    fun `cannot exceed what the model returned and keeps an empty deck empty`() {
        assertEquals(2, selectMisconceptions(listOf(m(ThinkingFlaw.CONFUSION, 1), m(ThinkingFlaw.REVERSAL, 2)), ThinkingFlaw.OMISSION).size)
        assertEquals(0, selectMisconceptions(emptyList(), ThinkingFlaw.OMISSION).size)
    }

    @Test
    fun `caps own-flaw items at eight and drops blank ones`() {
        val items = (1..10).map { m(ThinkingFlaw.REVERSAL, it) } + GeneratedMisconception("REVERSAL", " ", "r")
        assertEquals(8, selectMisconceptions(items, ThinkingFlaw.REVERSAL).size)
    }
}
