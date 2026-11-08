package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.TopPermissionResponse
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=$TOP_SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class TopTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute("TRUNCATE top_forum, discord_forum, vote_manager_role, discord_role, audit_entry CASCADE")
        bot(
            "/api/discord-roles",
            """{"roles":[{"id":"r-chair","name":"Präsidium","color":null,"position":1,"assignable":true}]}""",
            put = true,
        ).andExpect { status { isNoContent() } }
        reportForums()
    }

    @Test
    fun `admins see the forums with their tags and which of them are TOP forums`() {
        val view = mockMvc.get("/web/admin/tops") { cookie(admin) }.andExpect { status { isOk() } }

        assertEquals(listOf("tops", "gesperrt"), view.read("$.forums[*].name"))
        assertEquals(listOf(true, false), view.read("$.forums[*].usable"))
        assertEquals(listOf("x", "Erledigt", "Offen"), view.read("$.forums[0].tags[*].name"))
        assertEquals(emptyList<Any>(), view.read("$.selected"))
    }

    @Test
    fun `an admin picks a TOP forum with a done tag, which the audit log records once`() {
        set("""[{"forumId":"f-1","doneTagId":"11"}]""").andExpect { status { isNoContent() } }
        set("""[{"forumId":"f-1","doneTagId":"11"}]""").andExpect { status { isNoContent() } }

        val view = mockMvc.get("/web/admin/tops") { cookie(admin) }
        assertEquals(listOf("f-1"), view.read("$.selected[*].forumId"))
        assertEquals(listOf("11"), view.read("$.selected[*].doneTagId"))
        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("TOP_FORUMS_CHANGED"), log.read("$.entries[*].action"))
        assertEquals(listOf("TOP-Foren: #tops (Erledigt-Tag: Erledigt)"), log.read("$.entries[*].description"))
    }

    @Test
    fun `a forum the bot cannot manage, an unknown forum or a tag of another forum is refused`() {
        set("""[{"forumId":"f-2","doneTagId":null}]""").andExpect { status { isUnprocessableContent() } }
        set("""[{"forumId":"f-9","doneTagId":null}]""").andExpect { status { isBadRequest() } }
        set("""[{"forumId":"f-1","doneTagId":"99"}]""").andExpect { status { isBadRequest() } }
        set("""[{"forumId":"f-1","doneTagId":null},{"forumId":"f-1","doneTagId":null}]""").andExpect { status { isBadRequest() } }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM top_forum", Int::class.java))
    }

    @Test
    fun `a TOP forum stays picked after the bot lost its permissions there`() {
        set("""[{"forumId":"f-1","doneTagId":null}]""")
        reportForums(manageable = emptyList())

        set("""[{"forumId":"f-1","doneTagId":"12"}]""").andExpect { status { isNoContent() } }
    }

    @Test
    fun `only admins and members of the vote manager roles may close TOPs, and only in TOP forums`() {
        set("""[{"forumId":"f-1","doneTagId":"11"}]""")

        assertEquals(TopPermissionResponse.NotTopForum, permission("f-2", administrator = true))
        assertEquals(TopPermissionResponse.Forbidden, permission("f-1", roles = listOf("r-chair")))
        assertEquals(TopPermissionResponse.Allowed("11"), permission("f-1", administrator = true))

        mockMvc
            .put("/web/admin/votes/manager-roles") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleIds":["r-chair"]}"""
            }.andExpect { status { isNoContent() } }
        assertEquals(TopPermissionResponse.Allowed("11"), permission("f-1", roles = listOf("r-chair")))
    }

    @Test
    fun `closing and reopening reach the audit log`() {
        action(
            """{"forumId":"f-1","threadId":"500","threadName":"TOP 3: Haushalt","action":"CLOSED","reason":"Erledigt","archived":true}""",
        ).andExpect { status { isNoContent() } }
        action("""{"forumId":"f-1","threadId":"500","threadName":"TOP 3: Haushalt","action":"REOPENED"}""")
        action("""{"forumId":"f-1","threadId":"501","threadName":"TOP 4","action":"CLOSED"}""")

        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("TOP_CLOSED", "TOP_REOPENED", "TOP_CLOSED"), log.read("$.entries[*].action"))
        assertEquals(
            listOf(
                "TOP „TOP 4“ in #tops geschlossen",
                "TOP „TOP 3: Haushalt“ in #tops wieder geöffnet",
                "TOP „TOP 3: Haushalt“ in #tops geschlossen und archiviert, Grund: Erledigt",
            ),
            log.read("$.entries[*].description"),
        )
        assertEquals(listOf("Chair"), log.read<List<String>>("$.entries[*].actor.name").distinct())
    }

    @Test
    fun `only admins see and set the TOP forums, the bot needs the service token`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/tops") { cookie(user) }.andExpect { status { isForbidden() } }
        set("[]", user).andExpect { status { isForbidden() } }
        mockMvc.post("/api/tops/permission").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `malformed reports from the bot are refused`() {
        val tags = (1..21).joinToString(",") { """{"id":"$it","name":"t$it"}""" }
        bot(
            "/api/discord-forums",
            """{"forums":[{"id":"f-1","name":"tops","category":null,"position":0,"threadsManageable":true,"tags":[$tags]}]}""",
            put = true,
        ).andExpect { status { isBadRequest() } }
        action("""{"forumId":"f-1","threadId":"500","threadName":"x","action":"CLOSED","reason":"${"x".repeat(1001)}"}""")
            .andExpect { status { isBadRequest() } }
    }

    private fun reportForums(manageable: List<String> = listOf("f-1")) {
        val forums =
            listOf("f-1" to "tops", "f-2" to "gesperrt").mapIndexed { index, (id, name) ->
                """{"id":"$id","name":"$name","category":null,"position":$index,"threadsManageable":${id in manageable},
                    "tags":[{"id":"1$index","name":"x"},{"id":"1${index + 1}","name":"Erledigt"},{"id":"1${index + 2}","name":"Offen"}]}"""
            }
        bot("/api/discord-forums", """{"forums":[${forums.joinToString(",")}]}""", put = true).andExpect { status { isNoContent() } }
    }

    private fun set(
        forums: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/tops") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = """{"forums":$forums}"""
        }

    private fun permission(
        forumId: String,
        administrator: Boolean = false,
        roles: List<String> = emptyList(),
    ): TopPermissionResponse {
        val body = """{"actor":${actor(administrator, roles)},"forumId":"$forumId"}"""
        return BitsJson.decodeFromString(bot("/api/tops/permission", body).andReturn().response.contentAsString)
    }

    private fun action(report: String) = bot("/api/tops/actions", report.replaceFirst("{", """{"actor":${actor(true, emptyList())},"""))

    private fun actor(
        administrator: Boolean,
        roles: List<String>,
    ) = """{"discordUserId":"7","name":"Chair","avatarUrl":null,"roleIds":[${roles.joinToString(
        ",",
    ) { "\"$it\"" }}],"administrator":$administrator}"""

    private fun bot(
        path: String,
        body: String,
        put: Boolean = false,
    ): ResultActionsDsl =
        if (put) {
            mockMvc.put(path) {
                header("Authorization", "Bearer $TOP_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        } else {
            mockMvc.post(path) {
                header("Authorization", "Bearer $TOP_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val TOP_SERVICE_TOKEN = "test-service-token-at-least-32-chars"
