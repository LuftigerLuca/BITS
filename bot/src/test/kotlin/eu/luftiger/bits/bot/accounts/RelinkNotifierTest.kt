package eu.luftiger.bits.bot.accounts

import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.Language
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RelinkNotifierTest {
    private val sent = mutableListOf<Pair<String, String>>()
    private val notifier = RelinkNotifier({ userId, text -> sent += userId to text }, { Language.GERMAN })

    @Test
    fun `the message comes in the language of the previous account`() =
        runTest {
            val asked = mutableListOf<String>()
            val english =
                RelinkNotifier({ userId, text -> sent += userId to text }, { userId ->
                    asked += userId
                    Language.ENGLISH
                })

            english.handle(relinked)

            assertEquals(listOf("1"), asked)
            assertContains(sent.single().second, "no longer linked to the WarEra account **\\_Luftiger\\_**")
        }

    private val relinked =
        AccountLinkedEvent(
            discordUserId = "2",
            wareraUserId = "w1",
            previousDiscordUserId = "1",
            occurredAt = 1_800_000,
            wareraUsername = "_Luftiger_",
        )

    @Test
    fun `previous account gets exactly one message, even if the event comes twice`() =
        runTest {
            notifier.handle(relinked)
            notifier.handle(relinked)

            assertEquals(listOf("1"), sent.map { it.first })
        }

    @Test
    fun `first link sends nothing`() =
        runTest {
            notifier.handle(relinked.copy(previousDiscordUserId = null))

            assertEquals(emptyList(), sent)
        }

    @Test
    fun `closed direct messages do not fail the event`() =
        runTest {
            RelinkNotifier({ _, _ -> error("Cannot send messages to this user") }, { Language.GERMAN }).handle(relinked)
        }

    @Test
    fun `message names the account, the time and whom to ask`() {
        val message = RelinkNotifier.message(relinked, Language.GERMAN)

        assertContains(message, "**\\_Luftiger\\_**")
        assertContains(message, "<t:1800:f>")
        assertContains(message, "Admins")
    }
}
