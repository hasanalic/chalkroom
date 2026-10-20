package app.chalkroom.feature.pastenotes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmountLineTest {
    @Test
    fun `reading time matches the length`() {
        assertEquals("Under a minute to read", amountLine(159))
        assertEquals("About a minute to read", amountLine(1_200))
        assertEquals("About 3 minutes to read", amountLine(3_300))
        assertEquals("About 7 minutes to read", amountLine(8_000))
    }
}
