package eu.luftiger.bits.bot.tops

import eu.luftiger.bits.bot.backend.TopBackend
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.TopAction
import eu.luftiger.bits.shared.TopActionReport
import eu.luftiger.bits.shared.TopPermissionResponse
import eu.luftiger.bits.shared.VoteActor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopsTest {
    private val backend = FakeTopBackend()
    private val tops = Tops(backend)
    private val actor = VoteActor("42", "Chair", null, emptyList(), administrator = false)

    @Test
    fun `closing posts the message with the reason, tags and locks the post and reports it`() =
        runTest {
            val post = FakePost(appliedTagIds = listOf("t-urgent"))

            val reply = tops.close(post, actor, "<@42>", " Beschlossen ", archive = false, Language.GERMAN)

            assertEquals("Der TOP ist geschlossen.", reply)
            assertEquals(listOf("TOP geschlossen von <@42>.\nGrund: Beschlossen"), post.messages)
            assertEquals(listOf("t-urgent", "t-done"), post.appliedTagIds)
            assertTrue(post.locked)
            assertEquals(false, post.archived)
            assertEquals(
                listOf(TopActionReport(actor, "f-1", "p-1", "TOP 3", TopAction.CLOSED, "Beschlossen", false)),
                backend.reports,
            )
        }

    @Test
    fun `closing with archive archives the post in the same request`() =
        runTest {
            val post = FakePost()

            val reply = tops.close(post, actor, "<@42>", null, archive = true, Language.ENGLISH)

            assertEquals("The agenda item is closed and archived.", reply)
            assertEquals(listOf("TOP geschlossen von <@42>."), post.messages)
            assertEquals(1, post.updates)
            assertTrue(post.locked && post.archived)
        }

    @Test
    fun `a closed post, a forbidden member or another forum change nothing`() =
        runTest {
            assertEquals(
                "Dieser TOP ist schon geschlossen.",
                tops.close(FakePost(locked = true), actor, "<@42>", null, false, Language.GERMAN),
            )

            backend.permission = TopPermissionResponse.Forbidden
            assertTrue(tops.close(FakePost(), actor, "<@42>", null, false, Language.GERMAN).startsWith("TOPs schließen"))

            backend.permission = TopPermissionResponse.NotTopForum
            val post = FakePost()
            assertEquals(Tops.notInTopMessage(Language.GERMAN), tops.close(post, actor, "<@42>", null, false, Language.GERMAN))

            assertEquals(emptyList(), post.messages)
            assertEquals(0, post.updates)
            assertEquals(emptyList(), backend.reports)
        }

    @Test
    fun `without a done tag, or one deleted in Discord, the tags stay as they are`() =
        runTest {
            backend.permission = TopPermissionResponse.Allowed(null)
            val untagged = FakePost(appliedTagIds = listOf("t-urgent"))
            tops.close(untagged, actor, "<@42>", null, false, Language.GERMAN)
            assertEquals(listOf("t-urgent"), untagged.appliedTagIds)

            backend.permission = TopPermissionResponse.Allowed("t-gone")
            val gone = FakePost()
            tops.close(gone, actor, "<@42>", null, false, Language.GERMAN)
            assertEquals(emptyList(), gone.appliedTagIds)
            assertTrue(gone.locked)
        }

    @Test
    fun `a post with five tags is closed without the done tag, which the reply says`() =
        runTest {
            val post = FakePost(appliedTagIds = List(5) { "t-$it" })

            val reply = tops.close(post, actor, "<@42>", null, false, Language.GERMAN)

            assertEquals("Der TOP ist geschlossen. Das Erledigt-Tag fehlt, da der Post schon 5 Tags hat.", reply)
            assertEquals(List(5) { "t-$it" }, post.appliedTagIds)
            assertTrue(post.locked)
        }

    @Test
    fun `missing permissions or a refusal by Discord are explained and not reported`() =
        runTest {
            assertTrue(
                tops
                    .close(FakePost(botMayManage = false), actor, "<@42>", null, false, Language.GERMAN)
                    .startsWith("In diesem Forum darf ich keine Posts verwalten."),
            )

            val reply = tops.close(FakePost(failing = true), actor, "<@42>", null, false, Language.GERMAN)

            assertTrue(reply.startsWith("Discord hat das nicht angenommen."))
            assertEquals(emptyList(), backend.reports)
        }

    @Test
    fun `reopening unarchives and unlocks the post, removes the done tag, posts and reports it`() =
        runTest {
            val post = FakePost(locked = true, archived = true, appliedTagIds = listOf("t-urgent", "t-done"))

            val reply = tops.reopen(post, actor, "<@42>", Language.GERMAN)

            assertEquals("Der TOP ist wieder offen.", reply)
            assertEquals(listOf("t-urgent"), post.appliedTagIds)
            assertEquals(false, post.locked || post.archived)
            assertEquals(listOf("TOP wieder geöffnet von <@42>."), post.messages)
            assertEquals(listOf(TopAction.REOPENED), backend.reports.map { it.action })
        }

    @Test
    fun `reopening an open post changes nothing`() =
        runTest {
            val post = FakePost()

            assertEquals("Dieser TOP ist nicht geschlossen.", tops.reopen(post, actor, "<@42>", Language.GERMAN))
            assertEquals(0, post.updates)
            assertEquals(emptyList(), backend.reports)
        }

    @Test
    fun `a backend that does not take the report does not undo the reply`() =
        runTest {
            backend.failReports = true

            assertEquals("Der TOP ist geschlossen.", tops.close(FakePost(), actor, "<@42>", null, false, Language.GERMAN))
        }

    private class FakeTopBackend : TopBackend {
        var permission: TopPermissionResponse = TopPermissionResponse.Allowed("t-done")
        var failReports = false
        val reports = mutableListOf<TopActionReport>()

        override suspend fun topPermission(
            actor: VoteActor,
            forumId: String,
        ) = permission

        override suspend fun reportTopAction(report: TopActionReport) {
            if (failReports) error("backend down")
            reports += report
        }
    }

    private class FakePost(
        override var locked: Boolean = false,
        var archived: Boolean = false,
        override var appliedTagIds: List<String> = emptyList(),
        override val botMayManage: Boolean = true,
        private val failing: Boolean = false,
    ) : TopPost {
        override val id = "p-1"
        override val name = "TOP 3"
        override val forumId = "f-1"
        override val forumTagIds = setOf("t-urgent", "t-done") + List(5) { "t-$it" }
        val messages = mutableListOf<String>()
        var updates = 0

        override suspend fun send(message: String) {
            if (failing) error("Missing access")
            messages += message
        }

        override suspend fun update(
            tagIds: List<String>,
            locked: Boolean,
            archived: Boolean,
        ) {
            if (failing) error("Missing access")
            updates++
            appliedTagIds = tagIds
            this.locked = locked
            this.archived = archived
        }
    }
}
