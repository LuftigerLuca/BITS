package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RuleExplanation
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Message
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RolesCommandTest {
    private val explained =
        RoleExplanationResponse.Explained(
            wareraUsername = "_Luftiger_",
            roles =
                listOf(
                    ManagedRoleExplanation(
                        "1",
                        ManagedRoleOutcome.MET,
                        listOf(
                            RuleExplanation("Level ab 50", false, "Level 30 von 50"),
                            RuleExplanation("Staatsbürgerschaft Germany", true, null),
                        ),
                    ),
                    ManagedRoleExplanation(
                        "2",
                        ManagedRoleOutcome.NOT_MET,
                        listOf(
                            RuleExplanation("Level ab 50", false, "Level 30 von 50"),
                            RuleExplanation("Militärrang ab 10", false, "Militärrang 8 von 10"),
                        ),
                    ),
                    ManagedRoleExplanation(
                        "3",
                        ManagedRoleOutcome.UNDECIDED,
                        listOf(RuleExplanation("Präsident in Germany", null, "Regierung von Germany gerade nicht abrufbar")),
                    ),
                ),
        )

    @Test
    fun `message names the rule that is met and what is missing for each role`() {
        val message = RolesCommand.explanationMessage(explained)

        assertContains(message, "**\\_Luftiger\\_**")
        assertContains(message, "✅ <@&1>: Staatsbürgerschaft Germany\n")
        assertContains(message, "❌ <@&2>: Level 30 von 50, Militärrang 8 von 10 (eins davon genügt)\n")
        assertContains(
            message,
            "❔ <@&3>: gerade nicht prüfbar (Regierung von Germany gerade nicht abrufbar), die Rolle bleibt, wie sie ist\n",
        )
        assertContains(message, "nächsten Abgleich")
    }

    @Test
    fun `unlinked user is pointed to verify, and no rules are said as such`() {
        assertContains(RolesCommand.explanationMessage(RoleExplanationResponse.NotLinked), "`/verify`")
        assertEquals(
            "BITS vergibt gerade keine Rollen.",
            RolesCommand.explanationMessage(RoleExplanationResponse.Explained("Luftiger", emptyList())),
        )
    }

    @Test
    fun `many roles are cut to fit into one message`() {
        val role = explained.roles[1]
        val message = RolesCommand.explanationMessage(explained.copy(roles = List(100) { role.copy(roleId = "$it") }))

        assertTrue(message.length <= Message.MAX_CONTENT_LENGTH)
        assertContains(message, Regex("… und \\d+ weitere\n"))
    }

    @Test
    fun `command explains the own roles`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.explainRoles("42") } returns explained
            val replies = Replies()

            RolesCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand(RolesCommand.NAME, replies))
            runCurrent()

            assertEquals(listOf(RolesCommand.explanationMessage(explained)), replies.messages)
        }

    @Test
    fun `command says when WarEra or BITS cannot be reached`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.explainRoles("42") } throws WareraUnavailableException(RuntimeException("503"))
            val replies = Replies()

            RolesCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand(RolesCommand.NAME, replies))
            runCurrent()
            coEvery { backend.explainRoles("42") } throws IllegalStateException("connection refused")
            RolesCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand(RolesCommand.NAME, replies))
            runCurrent()

            assertEquals(listOf(RolesCommand.WARERA_UNAVAILABLE_MESSAGE, BITS_UNAVAILABLE_MESSAGE), replies.messages)
        }
}
