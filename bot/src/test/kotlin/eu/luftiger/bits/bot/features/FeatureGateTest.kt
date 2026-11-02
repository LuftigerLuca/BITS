package eu.luftiger.bits.bot.features

import eu.luftiger.bits.bot.backend.FeatureBackend
import eu.luftiger.bits.bot.backend.LanguageBackend
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.FeatureStates
import eu.luftiger.bits.shared.Language
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.DiscordLocale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureGateTest {
    private val delegated = CopyOnWriteArrayList<GenericEvent>()
    private val delegate = EventListener { delegated += it }

    private suspend fun features(states: FeatureStates): Features {
        val backend = mockk<FeatureBackend> { coEvery { features() } returns states }
        return Features(backend, mockk()).apply { refresh() }
    }

    private fun slashCommand(
        name: String,
        subcommand: String? = null,
    ) = mockk<SlashCommandInteractionEvent>(relaxed = true) {
        every { this@mockk.name } returns name
        every { userLocale } returns DiscordLocale.GERMAN
        every { subcommandName } returns subcommand
    }

    private fun button(id: String) =
        mockk<ButtonInteractionEvent>(relaxed = true) {
            every { componentId } returns id
            every { userLocale } returns DiscordLocale.GERMAN
        }

    @Test
    fun `passes the command on while its feature is on`() =
        runTest {
            val gate =
                FeatureGate(features(FeatureStates()), { it.profile }, "profile", languages = Languages(mockk()), delegate = delegate)
            val event = slashCommand("profile")

            gate.onEvent(event)

            assertEquals(listOf<GenericEvent>(event), delegated)
            verify(exactly = 0) { event.reply(any<String>()) }
        }

    @Test
    fun `answers the command with a hint while its feature is off`() =
        runTest {
            val gate =
                FeatureGate(
                    features(FeatureStates(profile = false)),
                    { it.profile },
                    "profile",
                    languages = Languages(mockk()),
                    delegate = delegate,
                )
            val event = slashCommand("profile")

            gate.onEvent(event)

            assertEquals(emptyList<GenericEvent>(), delegated)
            verify { event.reply(FeatureGate.disabledMessage(Language.GERMAN)) }
        }

    @Test
    fun `the hint follows the client without a known choice and the choice once it is known`() =
        runTest {
            val backend = mockk<LanguageBackend>(relaxed = true)
            val languages = Languages(backend)
            val gate =
                FeatureGate(features(FeatureStates(profile = false)), { it.profile }, "profile", languages = languages, delegate = delegate)
            val english =
                slashCommand("profile").also {
                    every { it.userLocale } returns DiscordLocale.ENGLISH_US
                    every { it.user.id } returns "42"
                }
            val german = slashCommand("profile").also { every { it.user.id } returns "42" }

            gate.onEvent(english)
            verify { english.reply(FeatureGate.disabledMessage(Language.ENGLISH)) }

            languages.set("42", Language.ENGLISH)
            gate.onEvent(german)
            verify { german.reply(FeatureGate.disabledMessage(Language.ENGLISH)) }
        }

    @Test
    fun `gates only the given subcommand`() =
        runTest {
            val gate =
                FeatureGate(
                    features(FeatureStates(votes = false)),
                    { it.votes },
                    "vote",
                    "start",
                    languages = Languages(mockk()),
                    delegate = delegate,
                )
            val start = slashCommand("vote", "start")
            val end = slashCommand("vote", "end")

            gate.onEvent(start)
            gate.onEvent(end)

            assertEquals(listOf<GenericEvent>(end), delegated)
            verify { start.reply(FeatureGate.disabledMessage(Language.GERMAN)) }
        }

    @Test
    fun `gates the buttons of the feature`() =
        runTest {
            val gate =
                FeatureGate(
                    features(FeatureStates(verification = false)),
                    { it.verification },
                    "verify",
                    buttonPrefix = "verify:check:",
                    languages = Languages(mockk()),
                    delegate = delegate,
                )
            val check = button("verify:check:ABC")
            val other = button("vote:7:YES")

            gate.onEvent(check)
            gate.onEvent(other)

            assertEquals(listOf<GenericEvent>(other), delegated)
            verify { check.reply(FeatureGate.disabledMessage(Language.GERMAN)) }
        }

    @Test
    fun `passes other events on while the feature is off`() =
        runTest {
            val gate =
                FeatureGate(
                    features(FeatureStates(votes = false)),
                    { it.votes },
                    "vote",
                    "start",
                    languages = Languages(mockk()),
                    delegate = delegate,
                )
            val autocomplete = mockk<CommandAutoCompleteInteractionEvent>(relaxed = true)
            val otherCommand = slashCommand("ping")

            gate.onEvent(autocomplete)
            gate.onEvent(otherCommand)

            assertEquals(listOf<GenericEvent>(autocomplete, otherCommand), delegated)
        }

    @Test
    fun `keeps the last states while the backend cannot be reached`() =
        runTest {
            val backend = mockk<FeatureBackend>()
            coEvery { backend.features() } returns FeatureStates(profile = false)
            val features = Features(backend, mockk())
            features.refresh()
            coEvery { backend.features() } throws IllegalStateException("connection refused")

            features.refresh()

            assertEquals(FeatureStates(profile = false), features.states)
        }

    @Test
    fun `everything is on until the backend answered`() {
        assertEquals(FeatureStates(), Features(mockk(), mockk()).states)
    }
}
