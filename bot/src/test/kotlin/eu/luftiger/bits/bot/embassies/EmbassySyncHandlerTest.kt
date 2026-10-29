package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.shared.EmbassySyncEvent
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadStatus
import eu.luftiger.bits.shared.EmbassyThreadTarget
import eu.luftiger.bits.shared.RoleChangeOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbassySyncHandlerTest {
    private val guild = FakeGuild()
    private val reports = mutableListOf<EmbassySyncReport>()
    private val handler = EmbassySyncHandler(guild) { _, report -> reports += report }

    @Test
    fun `an office holder gets the role first and then joins the thread`() =
        runTest {
            guild.addMember("1")
            val thread = guild.addThread("t-pl")

            handler.handle(
                sync(target("t-pl", "1" to "Außenminister von Polen"), ambassadors = mapOf("1" to "BITS: Außenminister von Polen")),
            )

            assertEquals(listOf("+role amb@1 (BITS: Außenminister von Polen)", "+t-pl@1"), guild.applied)
            assertEquals(setOf("1"), thread.members)
            val report = reports.single()
            assertEquals(
                listOf("Außenminister von Polen"),
                report.threads
                    .single()
                    .members
                    .map { it.reason },
            )
            assertEquals(listOf(RoleChangeOutcome.GRANTED, RoleChangeOutcome.GRANTED), report.changes.map { it.outcome })
            assertEquals(listOf(null, "t-pl"), report.changes.map { it.threadId })
        }

    @Test
    fun `members added in Discord leave, those who may manage threads and the bot stay`() =
        runTest {
            guild.addMember("1")
            guild.addMember("2")
            guild.addMember("mod")
            val thread = guild.addThread("t-pl", "2", "mod", "bot")
            thread.managers += "mod"

            handler.handle(sync(target("t-pl", "1" to "Präsident von Polen")))

            assertEquals(setOf("1", "mod", "bot"), thread.members)
            assertEquals(
                mapOf("1" to "Präsident von Polen", "mod" to "Darf Threads verwalten"),
                reports
                    .single()
                    .threads
                    .single()
                    .members
                    .associate { it.discordUserId to it.reason },
            )
        }

    @Test
    fun `a thread whose government is missing loses nobody, and its members keep the role`() =
        runTest {
            guild.addMember("1", "amb")
            val thread = guild.addThread("t-pl", "1")

            handler.handle(sync(target("t-pl", complete = false)))

            assertEquals(setOf("1"), thread.members)
            assertEquals(emptyList(), guild.applied)
        }

    @Test
    fun `members of host roles join every thread without getting the role, and leave once they lose the host role`() =
        runTest {
            guild.addMember("gov", "r-gov")
            guild.addMember("former")
            val pl = guild.addThread("t-pl", "former")
            val fr = guild.addThread("t-fr")

            handler.handle(sync(target("t-pl"), target("t-fr"), hostRoleIds = listOf("r-gov")))

            assertEquals(setOf("gov"), pl.members)
            assertEquals(setOf("gov"), fr.members)
            assertEquals(
                listOf("Gastgeber-Rolle @r-gov"),
                reports
                    .single()
                    .threads
                    .flatMap { it.members }
                    .map { it.reason }
                    .distinct(),
            )
            assertEquals(emptyList(), guild.applied.filter { it.startsWith("+role") })
        }

    @Test
    fun `the role is taken from everybody who is no ambassador`() =
        runTest {
            guild.addMember("1", "amb")
            guild.addMember("2", "amb")
            guild.addThread("t-pl", "1")

            handler.handle(sync(target("t-pl", "1" to "Manuell hinzugefügt"), ambassadors = mapOf("1" to "BITS: Manuell")))

            assertEquals(listOf("-role amb@2 (BITS: in keinem Botschafts-Thread mehr)"), guild.applied)
        }

    @Test
    fun `an archived thread is unarchived only if something changes`() =
        runTest {
            guild.addMember("1")
            guild.addMember("2")
            val unchanged = guild.addThread("t-pl", "1", archived = true)
            val changed = guild.addThread("t-fr", archived = true)

            handler.handle(sync(target("t-pl", "1" to "Präsident von Polen"), target("t-fr", "2" to "Präsident von Frankreich")))

            assertEquals(true, unchanged.archived)
            assertEquals(false, changed.archived)
            assertEquals(listOf("unarchive t-fr", "+t-fr@2"), guild.applied)
        }

    @Test
    fun `threads that are gone or not manageable are reported, the others are synced`() =
        runTest {
            guild.addMember("1")
            guild.addThread("t-locked").manageable = false
            val fr = guild.addThread("t-fr")
            guild.noPermission += "t-hidden"

            handler.handle(
                sync(
                    target("t-gone", "1" to "x"),
                    target("t-locked", "1" to "x"),
                    target("t-hidden", "1" to "x"),
                    target("t-fr", "1" to "x"),
                ),
            )

            assertEquals(setOf("1"), fr.members)
            assertEquals(
                mapOf(
                    "t-fr" to EmbassyThreadStatus.SYNCED,
                    "t-gone" to EmbassyThreadStatus.MISSING,
                    "t-hidden" to EmbassyThreadStatus.NO_PERMISSION,
                    "t-locked" to EmbassyThreadStatus.NO_PERMISSION,
                ),
                reports.single().threads.associate { it.threadId to it.status },
            )
        }

    @Test
    fun `a change Discord refuses is reported as failed and does not stop the others`() =
        runTest {
            guild.addMember("1")
            guild.addMember("2")
            val thread = guild.addThread("t-pl")
            guild.failing += "+t-pl@1"

            handler.handle(sync(target("t-pl", "1" to "x", "2" to "y")))

            assertEquals(setOf("2"), thread.members)
            assertEquals(
                mapOf("1" to RoleChangeOutcome.FAILED, "2" to RoleChangeOutcome.GRANTED),
                reports.single().changes.associate { it.discordUserId to it.outcome },
            )
        }

    @Test
    fun `a member who may not see the channel is reported with what the admins need to change`() =
        runTest {
            guild.addMember("1")
            val thread = guild.addThread("t-pl")
            thread.blind += "1"

            handler.handle(sync(target("t-pl", "1" to "x")))

            val change = reports.single().changes.single()
            assertEquals(RoleChangeOutcome.FAILED, change.outcome)
            assertEquals("Sieht #botschaft nicht: Die Botschafter-Rolle braucht dort „Kanal ansehen“.", change.reason)
        }

    @Test
    fun `a role above the bot is skipped and reported`() =
        runTest {
            guild.addMember("1")
            guild.addThread("t-pl")
            guild.roles["amb"] = EmbassyRole("amb", "Botschafter", assignable = false)

            handler.handle(sync(target("t-pl", "1" to "x"), ambassadors = mapOf("1" to "BITS: x")))

            assertEquals(listOf("+t-pl@1"), guild.applied)
            assertEquals(
                RoleChangeOutcome.SKIPPED,
                reports
                    .single()
                    .changes
                    .first { it.roleId == "amb" }
                    .outcome,
            )
        }

    private fun target(
        threadId: String,
        vararg members: Pair<String, String>,
        complete: Boolean = true,
    ) = EmbassyThreadTarget(threadId, "c-$threadId", threadId.removePrefix("t-"), mapOf(*members), complete)

    private fun sync(
        vararg threads: EmbassyThreadTarget,
        hostRoleIds: List<String> = emptyList(),
        ambassadors: Map<String, String> = emptyMap(),
    ) = EmbassySyncEvent(
        syncId = 1,
        threads = threads.toList(),
        hostRoleIds = hostRoleIds,
        ambassadorRoleId = "amb",
        ambassadors = ambassadors,
        occurredAt = 0,
    )

    /** Records every change it is asked for as `+t@member`, `-t@member`, `+role r@member (reason)` or `unarchive t`. */
    private class FakeGuild : EmbassyGuild {
        val members = LinkedHashMap<String, EmbassyMember>()
        val roles = mutableMapOf("amb" to EmbassyRole("amb", "Botschafter", assignable = true))
        val threads = mutableMapOf<String, FakeThread>()
        val noPermission = mutableSetOf<String>()
        val applied = mutableListOf<String>()
        val failing = mutableSetOf<String>()

        override val selfId = "bot"

        fun addMember(
            id: String,
            vararg roleIds: String,
        ) {
            members[id] = EmbassyMember(id, "Member $id", roleIds.toSet())
        }

        fun addThread(
            id: String,
            vararg memberIds: String,
            archived: Boolean = false,
        ) = FakeThread(id, memberIds.toMutableSet(), archived).also { threads[id] = it }

        override fun member(discordUserId: String) = members[discordUserId]

        override fun members() = members.values.toList()

        override fun role(roleId: String) = roles[roleId]

        override suspend fun thread(
            threadId: String,
            parentChannelId: String,
        ): ThreadLookup =
            when {
                threadId in noPermission -> ThreadLookup.NoPermission
                else -> threads[threadId]?.let { ThreadLookup.Found(it) } ?: ThreadLookup.Missing
            }

        override suspend fun grantRole(
            discordUserId: String,
            roleId: String,
            reason: String,
        ) = apply("+role $roleId@$discordUserId ($reason)")

        override suspend fun removeRole(
            discordUserId: String,
            roleId: String,
            reason: String,
        ) = apply("-role $roleId@$discordUserId ($reason)")

        fun apply(change: String) {
            if (change in failing) error("Discord refused $change")
            applied += change
        }

        inner class FakeThread(
            private val id: String,
            val members: MutableSet<String>,
            override var archived: Boolean,
        ) : EmbassyThreadChannel {
            val managers = mutableSetOf("bot")
            val blind = mutableSetOf<String>()
            override var manageable = true
            override val name = "Botschaft $id"

            override fun mayManageThreads(discordUserId: String) = discordUserId in managers

            override suspend fun memberIds() = members.toSet()

            override suspend fun unarchive(reason: String) {
                apply("unarchive $id")
                archived = false
            }

            override suspend fun rename(
                name: String,
                reason: String,
            ) = null

            override suspend fun add(discordUserId: String) {
                if (discordUserId in blind) throw MemberCannotViewException("botschaft", IllegalStateException())
                apply("+$id@$discordUserId")
                members += discordUserId
            }

            override suspend fun remove(discordUserId: String) {
                apply("-$id@$discordUserId")
                members -= discordUserId
            }
        }
    }
}
