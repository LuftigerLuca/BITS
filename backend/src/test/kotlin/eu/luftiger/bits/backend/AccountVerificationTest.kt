package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.account.internal.VerificationCleanup
import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.UnlinkAccountResponse
import eu.luftiger.bits.shared.VerificationCheckedEvent
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
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
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

    @Autowired
    lateinit var incompletePublications: IncompleteEventPublications

    @Autowired
    lateinit var cleanup: VerificationCleanup

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        jdbc.execute("TRUNCATE account_link, account_verification, role_rule, discord_role, event_publication")
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
        assertEquals(CheckVerificationResponse.CodeMissing, check("1", code))

        warera.rename("c1", "Waffen ${code.lowercase()} GmbH")
        clock.advance(Duration.ofSeconds(10))
        assertEquals(CheckVerificationResponse.Linked("Luftiger"), check("1", code))

        assertEquals("w1", jdbc.queryForObject("SELECT warera_user_id FROM account_link WHERE discord_user_id = '1'", String::class.java))
        assertEquals(
            AccountLinkedEvent(
                discordUserId = "1",
                wareraUserId = "w1",
                previousDiscordUserId = null,
                occurredAt = clock.millis(),
                wareraUsername = "Luftiger",
            ),
            events().filterIsInstance<AccountLinkedEvent>().single(),
        )
    }

    @Test
    fun `code can be used only once`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)

        assertEquals(CheckVerificationResponse.Linked("Luftiger"), check("1", code))
        assertEquals(CheckVerificationResponse.Invalid, check("1", code))
    }

    @Test
    fun `checks are throttled`() {
        val code = startedCode("1", "Luftiger")
        check("1", code)

        clock.advance(Duration.ofMillis(2_500))
        assertEquals(CheckVerificationResponse.TooEarly(retryAfterSeconds = 8), check("1", code))
    }

    @Test
    fun `two checks at the same time run once`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)
        warera.companyLookups.set(0)

        val responses = inParallel(2) { checkRequest("1", code) }

        assertEquals(setOf(CheckVerificationResponse.Checking, CheckVerificationResponse.TooEarly(10)), responses.toSet())
        assertEquals(CheckVerificationResponse.Linked("Luftiger"), awaitResult(code))
        assertEquals(1, warera.companyLookups.get())
        assertEquals(1, events().filterIsInstance<AccountLinkedEvent>().size)
    }

    @Test
    fun `two accounts linking the same WarEra account at the same time both succeed, the later one wins`() {
        val first = startedCode("1", "Luftiger")
        val second = startedCode("2", "Luftiger")
        warera.rename("c1", "$first $second")
        // Both checks find their code before either links, so neither sees the other's link.
        warera.holdCompanies(2)

        val results = inParallel(2) { i -> check(if (i == 0) "1" else "2", if (i == 0) first else second) }

        assertEquals(List(2) { CheckVerificationResponse.Linked("Luftiger") }, results)
        val linked = events().filterIsInstance<AccountLinkedEvent>()
        val winner = jdbc.queryForObject("SELECT discord_user_id FROM account_link WHERE warera_user_id = 'w1'", String::class.java)
        assertEquals(1, count("account_link"))
        assertEquals(winner, linked.last().discordUserId)
        assertEquals(setOf("1", "2") - winner, setOfNotNull(linked.last().previousDiscordUserId))
    }

    @Test
    fun `code in the name of a company the user does not own does not count`() {
        val code = startedCode("1", "Luftiger")
        // WarEra lists a company of somebody else among the user's companies.
        warera.companies.getValue("w1") += WareraCompany("c3", code, ownerId = "w2")

        assertEquals(CheckVerificationResponse.CodeMissing, check("1", code))
        assertEquals(0, count("account_link"))
    }

    @Test
    fun `code expires after 15 minutes`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)

        clock.advance(Duration.ofMinutes(15))
        assertEquals(CheckVerificationResponse.Expired, check("1", code))
        assertEquals(0, count("account_verification"))
    }

    @Test
    fun `expired verifications are deleted an hour after they expired`() {
        startedCode("1", "Luftiger")
        clock.advance(Duration.ofMinutes(10))
        startedCode("2", "Other")

        clock.advance(Duration.ofMinutes(66))
        cleanup.deleteExpired()
        assertEquals(listOf("2"), jdbc.queryForList("SELECT discord_user_id FROM account_verification", String::class.java))

        clock.advance(Duration.ofMinutes(10))
        cleanup.deleteExpired()
        assertEquals(0, count("account_verification"))
    }

    @Test
    fun `new verification invalidates the open code`() {
        val first = startedCode("1", "Luftiger")
        val second = startedCode("1", "Luftiger")
        warera.rename("c1", "$first $second")

        assertEquals(CheckVerificationResponse.Invalid, check("1", first))
        assertEquals(CheckVerificationResponse.Linked("Luftiger"), check("1", second))
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
        assertEquals(listOf(null, "1", null), events().filterIsInstance<AccountLinkedEvent>().map { it.previousDiscordUserId })
    }

    @Test
    fun `unlinking deletes the link and the open code of the user`() {
        link("1", "Luftiger")
        startedCode("1", "Other")
        link("2", "Other")

        unlink("1").andExpect { jsonPath("$.wareraUsername") { value("Luftiger") } }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_link WHERE discord_user_id = '1'", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_verification WHERE discord_user_id = '1'", Int::class.java))
        assertEquals(1, count("account_link"))
    }

    @Test
    fun `unlinking an account that is not linked says so`() {
        val body =
            unlink("1")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertEquals(UnlinkAccountResponse(wareraUsername = null), BitsJson.decodeFromString<UnlinkAccountResponse>(body))
    }

    @Test
    fun `unreachable WarEra keeps the code valid`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)
        warera.down = true

        assertEquals(CheckVerificationResponse.WareraUnavailable, check("1", code))

        warera.down = false
        clock.advance(Duration.ofSeconds(10))
        assertEquals(CheckVerificationResponse.Linked("Luftiger"), check("1", code))
    }

    @Test
    fun `accounts are linked while Redis cannot take events, which follow once it can`() {
        val code = startedCode("1", "Luftiger")
        warera.rename("c1", code)
        // A key of another type makes every XADD on the stream fail.
        redisTemplate.opsForValue().set(EventStream.KEY, "blocked")

        post("/api/verifications/check", """{"discordUserId":"1","code":"$code"}""").andExpect { jsonPath("$.type") { value("checking") } }
        repeat(100) {
            if (count("account_link") == 1 && count("event_publication") == 2) return@repeat
            Thread.sleep(50)
        }
        assertEquals(1, count("account_link"))
        assertEquals(2, count("event_publication"))

        redisTemplate.delete(EventStream.KEY)
        incompletePublications.resubmitIncompletePublications { true }

        assertEquals(
            setOf(AccountLinkedEvent::class, VerificationCheckedEvent::class),
            events().mapTo(HashSet()) { it::class },
        )
    }

    private fun link(
        discordUserId: String,
        username: String,
    ) {
        val code = startedCode(discordUserId, username)
        warera.rename(if (username == "Other") "c2" else "c1", code)
        assertEquals(CheckVerificationResponse.Linked(username), check(discordUserId, code))
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

    private fun unlink(discordUserId: String) = post("/api/verifications/unlink", """{"discordUserId":"$discordUserId"}""")

    /** The answer to the request, or the result of the background check that follows as event. */
    private fun check(
        discordUserId: String,
        code: String,
    ): CheckVerificationResponse {
        val before = checkedEvents(code).size
        val response = checkRequest(discordUserId, code)
        if (response != CheckVerificationResponse.Checking) return response
        repeat(100) {
            checkedEvents(code).getOrNull(before)?.let { return it.result }
            Thread.sleep(50)
        }
        fail("no result for the check of $code")
    }

    private fun checkRequest(
        discordUserId: String,
        code: String,
    ): CheckVerificationResponse {
        val body =
            post("/api/verifications/check", """{"discordUserId":"$discordUserId","code":"$code"}""")
                .andReturn()
                .response.contentAsString
        return BitsJson.decodeFromString<CheckVerificationResponse>(body)
    }

    /** Runs [block] on [threads] threads that start at the same moment. */
    private fun <T> inParallel(
        threads: Int,
        block: (Int) -> T,
    ): List<T> {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val futures = (0 until threads).map { i -> executor.submit<T> { start.await().let { block(i) } } }
            start.countDown()
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun awaitResult(code: String): CheckVerificationResponse {
        repeat(100) {
            checkedEvents(code).singleOrNull()?.let { return it.result }
            Thread.sleep(50)
        }
        fail("no single result for the check of $code: ${checkedEvents(code)}")
    }

    private fun checkedEvents(code: String) = events().filterIsInstance<VerificationCheckedEvent>().filter { it.code == code }

    private fun post(
        path: String,
        body: String,
    ) = mockMvc.post(path) {
        header("Authorization", "Bearer test-service-token-at-least-32-chars")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)

    private fun events(): List<BitsEvent> {
        jdbc.awaitOutbox()
        return redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }
    }

    /** Two WarEra users, each with one company: Luftiger (w1, c1) and Other (w2, c2). */
    class FakeWarera : WareraClient {
        private val users = listOf(WareraUser("w1", "Luftiger"), WareraUser("w2", "Other"))
        val companies = ConcurrentHashMap<String, MutableList<WareraCompany>>()
        val companyLookups = AtomicInteger()
        private var companyGate: CountDownLatch? = null
        var down = false

        fun reset() {
            companies.clear()
            companies["w1"] = mutableListOf(WareraCompany("c1", "Waffen GmbH", "w1"))
            companies["w2"] = mutableListOf(WareraCompany("c2", "Brot AG", "w2"))
            companyLookups.set(0)
            companyGate = null
            down = false
        }

        /** The next [lookups] lookups of company lists wait for each other. */
        fun holdCompanies(lookups: Int) {
            companyGate = CountDownLatch(lookups)
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

        override fun getCompanyIds(userId: String) =
            available {
                companyLookups.incrementAndGet()
                companyGate?.let {
                    it.countDown()
                    it.await(10, TimeUnit.SECONDS)
                }
                companies[userId].orEmpty().map { it.id }
            }

        override fun getCompany(companyId: String) = available { companies.values.flatten().first { it.id == companyId } }

        override fun getGovernment(countryId: String) = TODO()

        override fun searchMilitaryUnitIds(text: String) = TODO()

        override fun getMilitaryUnit(muId: String) = TODO()

        private fun <T> available(block: () -> T): T = if (down) throw WareraApiException("down") else block()
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
