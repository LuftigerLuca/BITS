package eu.luftiger.bits.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class VerificationTest {
    @Test
    fun `responses are identified by their type`() {
        val payload = """{"type":"too_early","retryAfterSeconds":7}"""

        assertEquals(
            CheckVerificationResponse.TooEarly(retryAfterSeconds = 7),
            BitsJson.decodeFromString(CheckVerificationResponse.serializer(), payload),
        )
        assertEquals(
            """{"type":"user_not_found"}""",
            BitsJson.encodeToString(StartVerificationResponse.serializer(), StartVerificationResponse.UserNotFound),
        )
    }

    @Test
    fun `account linked event survives an encode-decode round trip`() {
        val event = AccountLinkedEvent(discordUserId = "1", wareraUserId = "w1", previousDiscordUserId = null, occurredAt = 0)

        assertEquals(event, decodeEvent(event.encode()))
    }
}
