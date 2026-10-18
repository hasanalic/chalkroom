package app.chalkroom.feature.lesson

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClearingChoreographyTest {
    @Test
    fun `steps fire at the PRD timings`() = runTest {
        val fired = mutableListOf<Pair<ClearingStep, Long>>()
        ClearingChoreography.run { fired += it to currentTime }
        assertEquals(
            listOf(
                ClearingStep.HOLD to 0L,
                ClearingStep.FOG_RESOLVES to 480L,
                ClearingStep.DRIFT_TO_EYE to 620L,
                ClearingStep.EYES_WIDEN to 700L,
                ClearingStep.COUNTER_DROPS to 780L,
                ClearingStep.SPEAK to 900L,
            ),
            fired,
        )
    }
}
