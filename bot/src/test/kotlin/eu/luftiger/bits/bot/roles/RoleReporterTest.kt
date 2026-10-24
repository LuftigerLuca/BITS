package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.DiscordRoleDto
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.UnlinkAccountResponse
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleReporterTest {
    private val role = DiscordRoleDto(id = "r1", name = "Veteran", color = null, position = 1, assignable = true)
    private val backend = FakeBackend()

    @Test
    fun `a report the backend does not take is sent again until it goes through`() =
        runTest {
            backend.failures = 2
            val reporter = RoleReporter("100", backend, backgroundScope)

            reporter.report { listOf(role) }
            // advanceUntilIdle does not wait for the background scope.
            advanceTimeBy(600_000)

            assertEquals(listOf(listOf(role)), backend.reports)
            assertEquals(3, backend.attempts)
        }

    @Test
    fun `a newer report replaces the one that waits for a retry`() =
        runTest {
            backend.failures = Int.MAX_VALUE
            val reporter = RoleReporter("100", backend, backgroundScope)

            reporter.report { listOf(role) }
            advanceTimeBy(3_000)
            backend.failures = 0
            reporter.report { listOf(role.copy(name = "Minister")) }
            // advanceUntilIdle does not wait for the background scope.
            advanceTimeBy(600_000)

            assertEquals(listOf(listOf(role.copy(name = "Minister"))), backend.reports)
        }

    private class FakeBackend : BackendClient {
        val reports = mutableListOf<List<DiscordRoleDto>>()
        var failures = 0
        var attempts = 0

        override suspend fun reportRoles(roles: List<DiscordRoleDto>) {
            attempts++
            if (failures-- > 0) error("backend down")
            reports += roles
        }

        override suspend fun ping(discordUserId: String): PingResponse = TODO()

        override suspend fun startVerification(
            discordUserId: String,
            wareraUsername: String,
        ): StartVerificationResponse = TODO()

        override suspend fun checkVerification(
            discordUserId: String,
            code: String,
        ): CheckVerificationResponse = TODO()

        override suspend fun unlinkAccount(discordUserId: String): UnlinkAccountResponse = TODO()
    }
}
