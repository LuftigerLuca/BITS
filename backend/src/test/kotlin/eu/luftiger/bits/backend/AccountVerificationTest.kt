package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCompany
import eu.luftiger.bits.warera.WareraUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=test-token"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, AccountVerificationTest.Fakes::class)
class AccountVerificationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var warera: FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    @BeforeTest
    fun reset() {
        jdbc.execute("TRUNCATE account_link, account_verification")
        redisTemplate.delete(EventStream.KEY)
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
    }

    @Test
    fun `unknown name gets no code`() {
        start("1", "Nobody").andExpect { jsonPath("$.type") { value("user_not_found") } }
    }

    @Test
    fun `user without company gets no code`() {
        warera.companies.remove("w1")

        start("1", "luftiger").andExpect {
            jsonPath("$.type") { value("no_company") }
            jsonPath("$.wareraUsername") { value("Luftiger") }
        }
        assertEquals(0, count("account_verification"))
    }

    @Test
    fun `code in a company name links the accounts`() {
        val code = startedCode("1", "luftiger")
        check("1", code).andExpect { jsonPath("$.type") { value("code_missing") } }

        warera.rename("c1", "Waffen ${code.lowercase()} GmbH")
        clock.advance(Duration.ofSeconds(10))
        check("1", code).andExpect {
            jsonPath("$.type") { value("linked") }
            jsonPath("$.wareraUsername") { value("Luftiger") }
        }

        assertEquals("w1", jdbc.queryForObject("SELECT warera_user_id FROM account_link WHERE discord_user_id = '1'", String::class.java))
        assertEquals(
            AccountLinkedEvent(discordUserId = "1", wareraUserId = "w1", previousDiscordUserId = null, occurredAt = clock.millis()),
            events().single(),
        )
    }

    @Test
    fun `code can be used only once`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)

        check("1", code).andExpect { jsonPath("$.type") { value("linked") } }
        check("1", code).andExpect { jsonPath("$.type") { value("invalid") } }
    }

    @Test
    fun `checks are throttled`() {
        val code = startedCode("1", "Luftiger")
        check("1", code)

        clock.advance(Duration.ofMillis(2_500))
        check("1", code).andExpect {
            jsonPath("$.type") { value("too_early") }
            jsonPath("$.retryAfterSeconds") { value(8) }
        }
    }

    @Test
    fun `code expires after 15 minutes`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)

        clock.advance(Duration.ofMinutes(15))
        check("1", code).andExpect { jsonPath("$.type") { value("expired") } }
        assertEquals(0, count("account_verification"))
    }

    @Test
    fun `new verification invalidates the open code`() {
        val first = startedCode("1", "Luftiger")
        val second = startedCode("1", "Luftiger")
        warera.rename("c1", "$first $second")

        check("1", first).andExpect { jsonPath("$.type") { value("invalid") } }
        check("1", second).andExpect { jsonPath("$.type") { value("linked") } }
    }

    @Test
    fun `linking replaces the links of both accounts`() {
        link("1", "Luftiger")
        clock.advance(Duration.ofMinutes(1))
        link("2", "Luftiger")

        start("2", "Other").andExpect { jsonPath("$.linkedWareraUsername") { value("Luftiger") } }
        link("2", "Other")

        assertEquals(
            listOf("2" to "w2"),
            jdbc.query("SELECT discord_user_id, warera_user_id FROM account_link") { rs, _ -> rs.getString(1) to rs.getString(2) },
        )
        assertEquals(listOf(null, "1", null), events().map { (it as AccountLinkedEvent).previousDiscordUserId })
    }

    @Test
    fun `unreachable WarEra keeps the code valid`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)
        warera.down = true

        check("1", code).andExpect { status { isServiceUnavailable() } }

        warera.down = false
        clock.advance(Duration.ofSeconds(10))
        check("1", code).andExpect { jsonPath("$.type") { value("linked") } }
    }

    private fun link(
        discordUserId: String,
        username: String,
    ) {
        val code = startedCode(discordUserId, username)
        warera.rename(if (username == "Other") "c2" else "c1", code)
        check(discordUserId, code).andExpect { jsonPath("$.type") { value("linked") } }
    }

    private fun startedCode(
        discordUserId: String,
        username: String,
    ): String {
        val body =
            start(discordUserId, username)
                .andExpect { jsonPath("$.type") { value("started") } }
                .andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.code")
    }

    private fun start(
        discordUserId: String,
        username: String,
    ): ResultActionsDsl = post("/api/verifications", """{"discordUserId":"$discordUserId","wareraUsername":"$username"}""")

    private fun check(
        discordUserId: String,
        code: String,
    ): ResultActionsDsl = post("/api/verifications/check", """{"discordUserId":"$discordUserId","code":"$code"}""")

    private fun post(
        path: String,
        body: String,
    ) = mockMvc.post(path) {
        header("Authorization", "Bearer test-token")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)

    private fun events() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))!!
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    /** Two WarEra users, each with one company: Luftiger (w1, c1) and Other (w2, c2). */
    class FakeWarera : WareraClient {
        private val users = listOf(WareraUser("w1", "Luftiger"), WareraUser("w2", "Other"))
        val companies = mutableMapOf<String, MutableList<WareraCompany>>()
        var down = false

        fun reset() {
            companies.clear()
            companies["w1"] = mutableListOf(WareraCompany("c1", "Waffen GmbH"))
            companies["w2"] = mutableListOf(WareraCompany("c2", "Brot AG"))
            down = false
        }

        fun rename(
            companyId: String,
            name: String,
        ) {
            companies.values.forEach { list -> list.replaceAll { if (it.id == companyId) it.copy(name = name) else it } }
        }

        override fun getAllCountries() = TODO()

        // Like WarEra, the search is fuzzy and may return users whose name does not match exactly.
        override fun searchUserIds(text: String) =
            available { users.filter { it.username.startsWith(text.take(1), ignoreCase = true) }.map { it.id } }

        override fun getUser(userId: String) = available { users.first { it.id == userId } }

        override fun getCompanyIds(userId: String) = available { companies[userId].orEmpty().map { it.id } }

        override fun getCompany(companyId: String) = available { companies.values.flatten().first { it.id == companyId } }

        private fun <T> available(block: () -> T): T = if (down) throw WareraApiException("down") else block()
    }

    class MutableClock(
        var now: Instant = Instant.EPOCH,
    ) : Clock() {
        fun advance(duration: Duration) {
            now += duration
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fakes {
        @Bean
        @Primary
        fun fakeWareraClient() = FakeWarera()

        @Bean
        @Primary
        fun mutableClock() = MutableClock()
    }
}
