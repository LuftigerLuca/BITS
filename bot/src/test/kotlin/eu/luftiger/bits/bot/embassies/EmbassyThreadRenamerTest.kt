package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.shared.EmbassyThreadRename
import eu.luftiger.bits.shared.EmbassyThreadRenameEvent
import eu.luftiger.bits.shared.EmbassyThreadRenameReport
import eu.luftiger.bits.shared.FailedThreadRename
import eu.luftiger.bits.shared.ThreadRenameFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbassyThreadRenamerTest {
    private val guild = FakeGuild()
    private val reports = mutableListOf<EmbassyThreadRenameReport>()
    private val renamer = EmbassyThreadRenamer(guild) { reports += it }

    @Test
    fun `renames the threads and reports nothing when all went well`() =
        runTest {
            guild.threads["t-fr"] = FakeThread("botschaft-frankreich")
            guild.threads["t-pl"] = FakeThread("Botschaft Poland")

            renamer.handle(event("t-fr" to "France", "t-pl" to "Poland"))

            assertEquals("Botschaft France", guild.threads.getValue("t-fr").name)
            assertEquals(listOf("t-fr"), guild.renamed)
            assertEquals(emptyList(), reports)
        }

    @Test
    fun `archived threads are skipped`() =
        runTest {
            guild.threads["t-fr"] = FakeThread("botschaft-frankreich", archived = true)

            renamer.handle(event("t-fr" to "France"))

            assertEquals(emptyList(), guild.renamed)
            assertEquals(emptyList(), reports)
        }

    @Test
    fun `a thread that fails holds up none of the others and is reported`() =
        runTest {
            guild.threads["t-de"] = FakeThread("botschaft-deutschland", manageable = false)
            guild.threads["t-fr"] = FakeThread("botschaft-frankreich", refusal = IllegalStateException("Discord down"))
            guild.threads["t-it"] = FakeThread("botschaft-italien", refusal = ThreadRenameFailure.NO_PERMISSION)
            guild.threads["t-pl"] = FakeThread("botschaft-polen")

            renamer.handle(event("t-de" to "Germany", "t-fr" to "France", "t-gone" to "Spain", "t-it" to "Italy", "t-pl" to "Poland"))

            assertEquals(listOf("t-pl"), guild.renamed)
            assertEquals(
                listOf(
                    EmbassyThreadRenameReport(
                        listOf(
                            FailedThreadRename("t-de", "botschaft-deutschland", "Germany", ThreadRenameFailure.NO_PERMISSION),
                            FailedThreadRename("t-fr", "botschaft-frankreich", "France", ThreadRenameFailure.FAILED),
                            FailedThreadRename("t-gone", null, "Spain", ThreadRenameFailure.MISSING),
                            FailedThreadRename("t-it", "botschaft-italien", "Italy", ThreadRenameFailure.NO_PERMISSION),
                        ),
                    ),
                ),
                reports,
            )
        }

    private fun event(vararg threads: Pair<String, String>) =
        EmbassyThreadRenameEvent(
            threads.map { (id, country) -> EmbassyThreadRename(id, "c-1", "Botschaft $country", country) },
            occurredAt = 0,
        )

    private inner class FakeThread(
        override var name: String,
        override val archived: Boolean = false,
        override val manageable: Boolean = true,
        /** A [ThreadRenameFailure] to return or an exception to throw instead of renaming. */
        private val refusal: Any? = null,
    ) : EmbassyThreadChannel {
        override fun mayManageThreads(discordUserId: String) = false

        override suspend fun memberIds() = emptySet<String>()

        override suspend fun unarchive(reason: String) = error("not used")

        override suspend fun rename(
            name: String,
            reason: String,
        ): ThreadRenameFailure? {
            if (refusal is Exception) throw refusal
            if (refusal is ThreadRenameFailure) return refusal
            guild.renamed +=
                guild.threads.entries
                    .first { it.value === this }
                    .key
            this.name = name
            return null
        }

        override suspend fun add(discordUserId: String) = error("not used")

        override suspend fun remove(discordUserId: String) = error("not used")
    }

    private class FakeGuild : EmbassyGuild {
        val threads = LinkedHashMap<String, EmbassyThreadChannel>()
        val renamed = mutableListOf<String>()

        override val selfId = "bot"

        override fun member(discordUserId: String) = null

        override fun members() = emptyList<EmbassyMember>()

        override fun role(roleId: String) = null

        override suspend fun thread(
            threadId: String,
            parentChannelId: String,
        ) = threads[threadId]?.let { ThreadLookup.Found(it) } ?: ThreadLookup.Missing

        override suspend fun grantRole(
            discordUserId: String,
            roleId: String,
            reason: String,
        ) = error("not used")

        override suspend fun removeRole(
            discordUserId: String,
            roleId: String,
            reason: String,
        ) = error("not used")
    }
}
