package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.VerificationCheckedEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LoggingEventHandlerTest {
    @Test
    fun `handles every type of event`() =
        runTest {
            val handler = LoggingEventHandler()
            listOf(
                PingEvent("1", occurredAt = 0),
                AccountLinkedEvent("1", "w1", previousDiscordUserId = null, occurredAt = 0),
                VerificationCheckedEvent("1", "BITS-ABC234", CheckVerificationResponse.CodeMissing, occurredAt = 0),
                RoleSyncEvent(managedRoleIds = emptyList(), discordUserIds = null, members = emptyMap(), occurredAt = 0),
            ).forEach { handler.handle(it) }
        }
}
