package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.shared.MemberRateLimit
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

@SpringBootTest(properties = ["bits.service-token=$RATE_LIMIT_TOKEN", "bits.member-rate-limit.per-minute=2"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class MemberRateLimitTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        // Members nobody linked, so that the profile is answered without WarEra.
        jdbc.update("DELETE FROM account_link WHERE discord_user_id LIKE '900_'")
        redisTemplate.keys("${MemberRateLimit.KEY_PREFIX}*").let { if (it.isNotEmpty()) redisTemplate.delete(it) }
        clock.now = Instant.parse("2026-01-01T12:00:15Z")
    }

    @Test
    fun `a member who calls WarEra too often waits for the next minute, others do not`() {
        bot("/api/profiles", """{"discordUserId":"9001"}""").andExpect { status { isOk() } }
        bot("/api/role-explanations", """{"discordUserId":"9001"}""").andExpect { status { isOk() } }

        bot("/api/profiles", """{"discordUserId":"9001"}""").andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "45") }
        }
        bot("/api/verifications", """{"discordUserId":"9001","wareraUsername":"Luftiger"}""").andExpect { status { isTooManyRequests() } }
        bot("/api/profiles", """{"discordUserId":"9002"}""").andExpect { status { isOk() } }

        clock.advance(Duration.ofSeconds(45))
        bot("/api/profiles", """{"discordUserId":"9001"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `the roles on the account page count as well`() {
        val session =
            sessionTokens
                .cookie(
                    SessionUser.discord("9003", "Member", "https://avatar", Role.USER),
                ).let { Cookie(it.name, it.value) }
        bot("/api/profiles", """{"discordUserId":"9003"}""").andExpect { status { isOk() } }
        bot("/api/profiles", """{"discordUserId":"9003"}""").andExpect { status { isOk() } }

        mockMvc.get("/web/account/roles") { cookie(session) }.andExpect { status { isTooManyRequests() } }
    }

    private fun bot(
        path: String,
        body: String,
    ) = mockMvc.post(path) {
        header("Authorization", "Bearer $RATE_LIMIT_TOKEN")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }
}

private const val RATE_LIMIT_TOKEN = "test-service-token-at-least-32-chars"
