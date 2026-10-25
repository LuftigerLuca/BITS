package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.ProfileResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileCommandTest {
    private val found =
        ProfileResponse.Found(
            wareraUserId = "w1",
            wareraUsername = "_Luftiger_",
            level = 30,
            militaryRank = 12,
            countryName = "Germany",
            militaryUnitName = null,
            profileUrl = "https://app.warera.io/user/w1",
        )

    @Test
    fun `embed shows the profile with a link to WarEra`() {
        val embed = assertIs<ProfileCommand.Answer.Profile>(ProfileCommand.answer(found, own = true, mention = "<@42>")).embed

        assertEquals("\\_Luftiger\\_", embed.title)
        assertEquals("https://app.warera.io/user/w1", embed.url)
        assertEquals("WarEra-Profil von <@42>", embed.description)
        assertEquals(
            listOf("Level" to "30", "Militärrang" to "12", "Staatsbürgerschaft" to "Germany", "Military Unit" to "keine"),
            embed.fields.map { it.name to it.value },
        )
    }

    @Test
    fun `missing link points the user to verify, but not for someone else`() {
        val own = ProfileCommand.answer(ProfileResponse.NotLinked, own = true, mention = "<@42>")
        val other = ProfileCommand.answer(ProfileResponse.NotLinked, own = false, mention = "<@7>")

        assertEquals(
            ProfileCommand.Answer.Hint(
                "Dein Discord-Account ist mit keinem WarEra-Account verknüpft. Mit `/verify` verknüpfst du ihn.",
            ),
            own,
        )
        assertEquals(ProfileCommand.Answer.Hint("<@7> hat keinen WarEra-Account verknüpft."), other)
    }

    @Test
    fun `command shows the profile of the given member in public`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile("7") } returns found
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies, target = member("7")))
            runCurrent()

            assertEquals("WarEra-Profil von <@7>", replies.embeds.single().description)
            assertFalse(replies.deletedOriginal)
            assertEquals(emptyList(), replies.messages)
        }

    @Test
    fun `command without a member asks for the own profile and answers hints privately`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile("42") } returns ProfileResponse.NotLinked
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies))
            runCurrent()

            assertTrue(replies.deletedOriginal)
            assertEquals(1, replies.ephemeralMessages.size)
            assertEquals(replies.messages, replies.ephemeralMessages)
            assertEquals(emptyList(), replies.embeds)
        }

    @Test
    fun `command says when WarEra cannot be reached`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile(any()) } throws WareraUnavailableException(RuntimeException("503"))
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies))
            runCurrent()

            assertEquals(listOf(ProfileCommand.WARERA_UNAVAILABLE_MESSAGE), replies.ephemeralMessages)
        }

    @Test
    fun `command says BITS is not reachable when the backend fails`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile(any()) } throws IllegalStateException("connection refused")
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies))
            runCurrent()

            assertEquals(listOf(BITS_UNAVAILABLE_MESSAGE), replies.ephemeralMessages)
        }

    private fun member(id: String): User =
        mockk {
            every { this@mockk.id } returns id
            every { asMention } returns "<@$id>"
        }

    /** `/profil` of user 42, for [target] if given. */
    private fun profileCommand(
        replies: Replies,
        target: User? = null,
    ): SlashCommandInteractionEvent {
        val event = slashCommand(ProfileCommand.NAME, replies)
        val self = member("42")
        every { event.user } returns self
        every { event.getOption("nutzer") } returns target?.let { user -> mockk { every { asUser } returns user } }
        return event
    }
}
