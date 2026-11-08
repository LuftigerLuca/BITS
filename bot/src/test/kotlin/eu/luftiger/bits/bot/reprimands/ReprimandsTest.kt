package eu.luftiger.bits.bot.reprimands

import eu.luftiger.bits.bot.backend.ReprimandBackend
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.ProtectedCause
import eu.luftiger.bits.shared.ReprimandMember
import eu.luftiger.bits.shared.ReprimandRequest
import eu.luftiger.bits.shared.ReprimandResponse
import eu.luftiger.bits.shared.ReprimandTimeoutEvent
import eu.luftiger.bits.shared.ReprimandTimeoutProblemReport
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.MessageEmbed
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReprimandsTest {
    private val until = Instant.parse("2026-01-01T12:10:00Z")
    private val backend = FakeReprimandBackend()
    private val guild = FakeReprimandGuild()
    private val directMessages = mutableListOf<Pair<String, String>>()
    private val posted = mutableListOf<MessageEmbed>()
    private var targetLanguage = Language.GERMAN
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val reprimands =
        Reprimands(
            backend,
            guild,
            { id, text -> directMessages += id to text },
            { targetLanguage },
            { "WarEra DE" },
            { _, call -> runBlocking { call() } },
            now = { now },
        )
    private val actor = ReprimandMember("7", "Mod")
    private val target = ReprimandMember("100", "Spammer")

    @Test
    fun `a reprimand times the member out, writes to them and is posted in the channel`() =
        runTest {
            targetLanguage = Language.ENGLISH

            val reply = reprimand(" Spam ")

            assertEquals("Spammer ist gerügt (2. Rüge) und hat einen Timeout von 10 Minuten.", reply)
            assertEquals(listOf(ReprimandRequest(actor, target, " Spam ")), backend.requests)
            assertEquals(listOf("100" to until), guild.timeouts)
            assertEquals(
                listOf(
                    "100" to
                        "You were reprimanded on **WarEra DE**. Reason: Spam\nThis is reprimand number 2 for you. You are timed out for 10 minutes.",
                ),
                directMessages,
            )
            assertEquals(
                listOf("Mitglied" to "<@100>", "Anzahl" to "2. Rüge", "Timeout" to "10 Minuten", "Grund" to "Spam"),
                posted.single().fields.map { it.name to it.value },
            )
        }

    @Test
    fun `a timeout Discord refuses still counts the reprimand and goes to the system log`() =
        runTest {
            guild.failure = TimeoutFailed("Das Mitglied steht über der höchsten Rolle des Bots.")

            val reply = reprimand()

            assertTrue(reply.startsWith("Spammer ist gerügt (2. Rüge), aber den Timeout konnte ich nicht setzen"))
            assertEquals(
                listOf(
                    ReprimandTimeoutProblemReport(
                        "100",
                        "Spammer",
                        "Die 2. Rüge zählt, aber der Timeout ließ sich nicht setzen. Das Mitglied steht über der höchsten Rolle des Bots.",
                        reprimandId = 5,
                    ),
                ),
                backend.problems,
            )
            assertEquals(listOf("Mitglied", "Anzahl", "Grund"), posted.single().fields.map { it.name })
            assertTrue("Timeout" !in directMessages.single().second)
        }

    @Test
    fun `closed direct messages and a channel the bot may not post in do not stop the reprimand`() =
        runTest {
            val reprimands =
                Reprimands(
                    backend,
                    guild,
                    { _, _ -> error("closed") },
                    { Language.GERMAN },
                    { "WarEra DE" },
                    { _, _ -> },
                )

            val reply = reprimands.reprimand(actor, target, "<@100>", "Spam", { error("no permission") }, Language.GERMAN)

            assertEquals(
                "Spammer ist gerügt (2. Rüge) und hat einen Timeout von 10 Minuten. Die Direktnachricht kam nicht an, z. B. " +
                    "weil das Mitglied keine annimmt. In diesem Kanal darf ich die Rüge nicht posten.",
                reply,
            )
            assertEquals(listOf("100" to until), guild.timeouts)
        }

    @Test
    fun `a refusal of the backend changes nothing in Discord`() =
        runTest {
            backend.response = ReprimandResponse.Forbidden
            assertTrue(reprimand().startsWith("Rügen dürfen nur Admins"))
            backend.response = ReprimandResponse.Disabled
            assertEquals("Reprimands are switched off at the moment.", reprimand(language = Language.ENGLISH))
            backend.response = ReprimandResponse.Protected(ProtectedCause.SELF)
            assertEquals("Du kannst dich nicht selbst rügen.", reprimand())
            backend.response = ReprimandResponse.Protected(ProtectedCause.REPRIMANDER)
            assertEquals("Mitglieder, die selbst rügen dürfen, lassen sich nicht rügen.", reprimand())

            assertEquals(emptyList(), guild.timeouts)
            assertEquals(emptyList(), directMessages)
            assertEquals(emptyList(), posted)
        }

    @Test
    fun `a withdrawn reprimand shortens or lifts the timeout of a member who is still there`() =
        runTest {
            reprimands.onTimeout(ReprimandTimeoutEvent("100", until.toEpochMilli(), 0))
            reprimands.onTimeout(ReprimandTimeoutEvent("100", null, 0))
            // An end in the past lifts the timeout as well.
            reprimands.onTimeout(ReprimandTimeoutEvent("100", now.minusSeconds(1).toEpochMilli(), 0))
            reprimands.onTimeout(ReprimandTimeoutEvent("999", null, 0))

            assertEquals(listOf("100" to until), guild.timeouts)
            assertEquals(listOf("100", "100"), guild.removed)
        }

    @Test
    fun `a timeout that cannot be lifted goes to the system log without failing the event`() =
        runTest {
            guild.failure = TimeoutFailed("Dem Bot fehlt das Recht.")

            reprimands.onTimeout(ReprimandTimeoutEvent("100", null, 0))

            assertEquals(
                listOf(
                    ReprimandTimeoutProblemReport(
                        "100",
                        "Spammer",
                        "Der Timeout ließ sich nach der Rücknahme einer Rüge nicht ändern. Dem Bot fehlt das Recht.",
                    ),
                ),
                backend.problems,
            )
        }

    @Test
    fun `durations are given in the largest unit that fits`() {
        assertEquals("1 Minute", ReprimandMessages.duration(1, Language.GERMAN))
        assertEquals("90 minutes", ReprimandMessages.duration(90, Language.ENGLISH))
        assertEquals("2 Stunden", ReprimandMessages.duration(120, Language.GERMAN))
        assertEquals("28 days", ReprimandMessages.duration(28 * 24 * 60, Language.ENGLISH))
    }

    private suspend fun reprimand(
        reason: String = "Spam",
        language: Language = Language.GERMAN,
    ) = reprimands.reprimand(actor, target, "<@100>", reason, { posted += it }, language)

    private inner class FakeReprimandBackend : ReprimandBackend {
        var response: ReprimandResponse = ReprimandResponse.Reprimanded(5, 2, 10, until.toEpochMilli())
        val requests = mutableListOf<ReprimandRequest>()
        val problems = mutableListOf<ReprimandTimeoutProblemReport>()

        override suspend fun reprimand(request: ReprimandRequest): ReprimandResponse {
            requests += request
            return response
        }

        override suspend fun reportReprimandTimeoutProblem(report: ReprimandTimeoutProblemReport) {
            problems += report
        }
    }

    private class FakeReprimandGuild : ReprimandGuild {
        var failure: TimeoutFailed? = null
        val timeouts = mutableListOf<Pair<String, Instant>>()
        val removed = mutableListOf<String>()

        override suspend fun timeoutUntil(
            discordUserId: String,
            until: Instant,
        ) {
            failure?.let { throw it }
            timeouts += discordUserId to until
        }

        override suspend fun removeTimeout(discordUserId: String) {
            failure?.let { throw it }
            removed += discordUserId
        }

        override fun memberName(discordUserId: String) = if (discordUserId == "100") "Spammer" else null
    }
}
