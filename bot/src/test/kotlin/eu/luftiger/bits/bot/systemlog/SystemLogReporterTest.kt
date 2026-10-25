package eu.luftiger.bits.bot.systemlog

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.UndeliveredEventReport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SystemLogReporterTest {
    private val backend = mockk<BackendClient>()
    private val report = RoleSyncReport(memberCount = 1, staleMemberCount = 0, changes = emptyList())

    @Test
    fun `a report the backend does not take is sent again until it goes through`() =
        runTest {
            var failures = 2
            coEvery { backend.reportRoleSync(7, report) } answers { if (failures-- > 0) error("backend down") }

            SystemLogReporter(backend, backgroundScope, mockk()).reportRoleSync(7, report)
            // advanceUntilIdle does not wait for the background scope.
            advanceTimeBy(600_000)

            coVerify(exactly = 3) { backend.reportRoleSync(7, report) }
        }

    @Test
    fun `a report is given up after about an hour`() =
        runTest {
            val undelivered = UndeliveredEventReport("1-0", "ping", 5)
            coEvery { backend.reportUndeliveredEvent(undelivered) } throws IllegalStateException("backend down")

            SystemLogReporter(backend, backgroundScope, mockk()).reportUndeliveredEvent(undelivered)
            advanceTimeBy(24 * 3_600_000L)

            coVerify(exactly = SystemLogReporter.MAX_ATTEMPTS) { backend.reportUndeliveredEvent(undelivered) }
        }
}
