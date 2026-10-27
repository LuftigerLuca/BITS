package eu.luftiger.bits.bot.features

import eu.luftiger.bits.bot.backend.FeatureBackend
import eu.luftiger.bits.shared.FeatureStates
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
        every { subcommandName } returns subcommand
    }

    private fun button(id: String) = mockk<ButtonInteractionEvent>(relaxed = true) { every { componentId } returns id }

    @Test
    fun `passes the command on while its feature is on`() =
        runTest {
            val gate = FeatureGate(features(FeatureStates()), { it.profile }, "profil", delegate = delegate)
            val event = slashCommand("profil")

            gate.onEvent(event)

            assertEquals(listOf<GenericEvent>(event), delegated)
            verify(exactly = 0) { event.reply(any<String>()) }
        }

    @Test
    fun `answers the command with a hint while its feature is off`() =
        runTest {
            val gate = FeatureGate(features(FeatureStates(profile = false)), { it.profile }, "profil", delegate = delegate)
            val event = slashCommand("profil")

            gate.onEvent(event)

            assertEquals(emptyList<GenericEvent>(), delegated)
            verify { event.reply(FeatureGate.DISABLED_MESSAGE) }
        }

    @Test
    fun `gates only the given subcommand`() =
        runTest {
            val gate = FeatureGate(features(FeatureStates(votes = false)), { it.votes }, "abstimmung", "starten", delegate = delegate)
            val start = slashCommand("abstimmung", "starten")
            val end = slashCommand("abstimmung", "beenden")

            gate.onEvent(start)
            gate.onEvent(end)

            assertEquals(listOf<GenericEvent>(end), delegated)
            verify { start.reply(FeatureGate.DISABLED_MESSAGE) }
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
                    delegate = delegate,
                )
            val check = button("verify:check:ABC")
            val other = button("vote:7:YES")

            gate.onEvent(check)
            gate.onEvent(other)

            assertEquals(listOf<GenericEvent>(other), delegated)
            verify { check.reply(FeatureGate.DISABLED_MESSAGE) }
        }

    @Test
    fun `passes other events on while the feature is off`() =
        runTest {
            val gate = FeatureGate(features(FeatureStates(votes = false)), { it.votes }, "abstimmung", "starten", delegate = delegate)
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
