package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.FeatureStates
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
import org.springframework.test.web.servlet.put
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=$SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class FeatureTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }
    private val user by lazy { session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER)) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        jdbc.execute("TRUNCATE feature_setting, audit_entry, event_publication")
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
    }

    /** Other tests share the database and expect every feature on. */
    @AfterTest
    fun cleanUp() {
        awaitBackgroundWork()
        jdbc.execute("TRUNCATE feature_setting")
    }

    @Test
    fun `every feature is on until an admin switches it off`() {
        val features = mockMvc.get("/web/features") { cookie(user) }.andExpect { status { isOk() } }

        assertEquals(
            listOf("VERIFICATION", "ROLE_SYNC", "NICKNAMES", "PROFILE", "VOTES", "EMBASSIES"),
            features.read("$[*].feature"),
        )
        assertEquals(List(6) { true }, features.read("$[*].enabled"))
    }

    @Test
    fun `an admin switches a feature off and on, which is recorded once per change`() {
        setFeature("PROFILE", false).andExpect { status { isNoContent() } }
        // The same state again changes nothing.
        setFeature("PROFILE", false).andExpect { status { isNoContent() } }

        assertEquals(listOf(false), mockMvc.get("/web/features") { cookie(user) }.read("$[?(@.feature == 'PROFILE')].enabled"))

        setFeature("PROFILE", true)

        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("FEATURE_TOGGLED", "FEATURE_TOGGLED"), log.read("$.entries[*].action"))
        assertEquals(listOf("Profil eingeschaltet", "Profil ausgeschaltet"), log.read("$.entries[*].description"))
    }

    @Test
    fun `only admins switch features, and only known ones`() {
        setFeature("VOTES", false, user).andExpect { status { isForbidden() } }
        setFeature("UNKNOWN", false).andExpect { status { isBadRequest() } }
        mockMvc.get("/web/features").andExpect { status { isUnauthorized() } }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM feature_setting", Int::class.java))
    }

    @Test
    fun `the bot reads the features with its commands, with the service token only`() {
        setFeature("VOTES", false)
        setFeature("VERIFICATION", false)

        val body =
            mockMvc
                .get("/api/features") { header("Authorization", "Bearer $SERVICE_TOKEN") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        // Read as the bot does: fields left out are on.
        assertEquals(FeatureStates(verification = false, votes = false), BitsJson.decodeFromString<FeatureStates>(body))
        mockMvc.get("/api/features") { cookie(admin) }.andExpect { status { isUnauthorized() } }
    }

    private fun setFeature(
        feature: String,
        enabled: Boolean,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/features/$feature") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":$enabled}"""
        }

    /** Switching the role sync or nicknames on starts a sync through the registry. */
    private fun awaitBackgroundWork() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
    }

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val SERVICE_TOKEN = "test-service-token-at-least-32-chars"
