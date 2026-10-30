package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
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
import kotlin.test.Test
import kotlin.test.assertEquals

/** Overlong input from the dashboard ends in a 400 before it reaches WarEra or the database, see `WebInput`. */
@SpringBootTest(properties = ["bits.service-token=$LIMIT_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class InputLimitTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    private val admin by lazy { sessionTokens.cookie(SessionUser.admin("admin")).let { Cookie(it.name, it.value) } }

    @Test
    fun `an overlong search term is rejected before WarEra is asked`() {
        // Reaching WarEra would end in a 503.
        warera.militaryUnitsDown = true
        warera.down = true

        mockMvc
            .get("/web/admin/role-sync/military-units") {
                cookie(admin)
                param("query", "x".repeat(101))
            }.andExpectInvalid()
        warera.reset()
    }

    @Test
    fun `an overlong field of a body is rejected before anything is saved`() {
        val before = jdbc.queryForObject("SELECT count(*) FROM dsk", Int::class.java)

        mockMvc
            .post("/web/admin/military-units/dsks") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"${"x".repeat(101)}"}"""
            }.andExpectInvalid()

        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM dsk", Int::class.java))
    }

    @Test
    fun `lists are limited in size and in their elements`() {
        val tooMany = (1..251).joinToString(",") { "\"$it\"" }
        managerRoles("""{"roleIds":[$tooMany]}""").andExpectInvalid()
        // Spring's answer for a failed validation, not the service's for an unknown role.
        managerRoles("""{"roleIds":["${"1".repeat(33)}"]}""").andExpectInvalid().andExpect {
            jsonPath("$.detail") { value("Invalid request content.") }
        }
    }

    @Test
    fun `an overlong path variable is rejected`() {
        mockMvc.get("/web/admin/members/${"1".repeat(33)}") { cookie(admin) }.andExpectInvalid()
    }

    @Test
    fun `the bot gets a 400 for an overlong avatar URL`() {
        mockMvc
            .post("/api/votes/1/close") {
                header("Authorization", "Bearer $LIMIT_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"actor":{"discordUserId":"42","name":"Luca","avatarUrl":"${"x".repeat(
                        513,
                    )}","roleIds":[],"administrator":true},"cancel":false}"""
            }.andExpectInvalid()
    }

    private fun managerRoles(body: String) =
        mockMvc.put("/web/admin/votes/manager-roles") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ResultActionsDsl.andExpectInvalid() =
        andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        }
}

private const val LIMIT_TOKEN = "test-service-token-at-least-32-chars"
