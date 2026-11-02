package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.LanguageBackend
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.UnlinkAccountResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.interactions.DiscordLocale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LanguageCommandTest {
    private val backend = mockk<LanguageBackend>(relaxed = true) { coEvery { languagePreference(any()) } returns null }

    @Test
    fun `stores the choice and confirms it in the new language`() =
        runTest {
            val replies = Replies()

            LanguageCommand(Languages(backend), backgroundScope)
                .onSlashCommandInteraction(slashCommand("language", replies, mapOf("language" to "en")))
            runCurrent()

            coVerify { backend.setLanguagePreference("42", Language.ENGLISH) }
            assertContains(replies.messages.single(), "from now on, BITS writes to you in English")
        }

    @Test
    fun `automatic deletes the choice and confirms in the language of the client`() =
        runTest {
            val replies = Replies()
            val event = slashCommand("language", replies, mapOf("language" to "auto"))
            every { event.userLocale } returns DiscordLocale.ENGLISH_US

            LanguageCommand(Languages(backend), backgroundScope).onSlashCommandInteraction(event)
            runCurrent()

            coVerify { backend.setLanguagePreference("42", null) }
            assertEquals(LanguageCommand.savedMessage(null, Language.ENGLISH), replies.messages.single())
        }

    @Test
    fun `the buttons after linking store the language and confirm in it`() =
        runTest {
            val replies = Replies()

            LanguageCommand(Languages(backend), backgroundScope)
                .onButtonInteraction(buttonClick(LanguageCommand.ENGLISH_BUTTON, replies))
            runCurrent()

            coVerify { backend.setLanguagePreference("42", Language.ENGLISH) }
            assertEquals(listOf(LanguageCommand.savedMessage(Language.ENGLISH, Language.ENGLISH)), replies.messages)
        }

    @Test
    fun `other buttons are left alone`() =
        runTest {
            val replies = Replies()

            LanguageCommand(Languages(backend), backgroundScope).onButtonInteraction(buttonClick("verify:check:ABC", replies))
            runCurrent()

            coVerify(exactly = 0) { backend.setLanguagePreference(any(), any()) }
            assertEquals(emptyList(), replies.messages)
        }

    @Test
    fun `says BITS is not reachable when the backend fails`() =
        runTest {
            coEvery { backend.setLanguagePreference(any(), any()) } throws IllegalStateException("connection refused")
            val replies = Replies()

            LanguageCommand(Languages(backend), backgroundScope)
                .onSlashCommandInteraction(slashCommand("language", replies, mapOf("language" to "en")))
            runCurrent()

            assertEquals(listOf(bitsUnavailableMessage(Language.GERMAN)), replies.messages)
        }

    @Test
    fun `only the English commands are registered, with German descriptions`() {
        assertEquals(
            setOf("ping", "verify", "unverify", "nickname", "profile", "roles", "language", "vote"),
            BOT_COMMANDS.mapTo(HashSet()) { it.name },
        )
        BOT_COMMANDS.forEach { command ->
            assertEquals(emptyMap(), command.nameLocalizations.toMap(), command.name)
            assertContains(command.descriptionLocalizations.toMap().keys, DiscordLocale.GERMAN, command.name)
        }
        val vote = BOT_COMMANDS.single { it.name == "vote" }
        assertEquals(listOf("start", "end", "cancel"), vote.subcommands.map { it.name })
        assertEquals(
            listOf("question", "role", "channel", "end", "threshold", "choice"),
            vote.subcommands
                .first()
                .options
                .map { it.name },
        )
    }

    @Test
    fun `with English set, private replies are English even in a German client`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.unlinkAccount("42") } returns
                eu.luftiger.bits.shared
                    .UnlinkAccountResponse(null)
            val replies = Replies()

            UnverifyCommand(backend, backgroundScope, languages(Language.ENGLISH))
                .onSlashCommandInteraction(slashCommand("unverify", replies))
            runCurrent()

            assertEquals(listOf("Your Discord account is not linked to a WarEra account."), replies.messages)
        }

    @Test
    fun `without a choice, an English client gets English`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.ping("42") } throws IllegalStateException("connection refused")
            val replies = Replies()
            val event = slashCommand("ping", replies)
            every { event.userLocale } returns DiscordLocale.ENGLISH_US

            PingCommand(backend, backgroundScope, languages()).onSlashCommandInteraction(event)
            runCurrent()

            assertEquals(listOf(bitsUnavailableMessage(Language.ENGLISH)), replies.messages)
        }

    @Test
    fun `roles ask the backend in the language of the member`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.explainRoles("42", Language.ENGLISH) } returns RoleExplanationResponse.NotLinked
            val replies = Replies()

            RolesCommand(backend, backgroundScope, languages(Language.ENGLISH))
                .onSlashCommandInteraction(slashCommand("roles", replies))
            runCurrent()

            assertContains(replies.messages.single(), "Your Discord account is not linked")
        }
}
