package eu.luftiger.bits.bot.commands

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingChecksTest {
    private val timedOut = mutableListOf<String>()

    @Test
    fun `a result answers the waiting check once`() =
        runTest {
            val checks = PendingChecks<String>(backgroundScope, Duration.ofMinutes(2)) { timedOut += it }
            checks.add("BITS-ABC234", "hook")

            assertEquals("hook", checks.complete("BITS-ABC234"))
            assertNull(checks.complete("BITS-ABC234"))
            advanceTimeBy(Duration.ofMinutes(3).toMillis())
            assertEquals(emptyList(), timedOut)
        }

    @Test
    fun `a check without result times out, and a late result is dropped`() =
        runTest {
            val checks = PendingChecks<String>(backgroundScope, Duration.ofMinutes(2)) { timedOut += it }
            checks.add("BITS-ABC234", "hook")

            advanceTimeBy(Duration.ofMinutes(2).toMillis() - 1)
            assertEquals(emptyList(), timedOut)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("hook"), timedOut)
            assertNull(checks.complete("BITS-ABC234"))
        }

    @Test
    fun `unknown codes have nobody waiting`() =
        runTest {
            assertNull(PendingChecks<String>(backgroundScope, Duration.ofMinutes(2)) { timedOut += it }.complete("BITS-XYZ789"))
        }
}
