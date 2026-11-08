package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.internal.DiscordApi
import eu.luftiger.bits.backend.auth.internal.DiscordGrantRevokedException
import eu.luftiger.bits.backend.auth.internal.DiscordGuild
import eu.luftiger.bits.backend.auth.internal.DiscordGuildMember
import eu.luftiger.bits.backend.auth.internal.DiscordTokens
import eu.luftiger.bits.backend.auth.internal.DiscordUser
import eu.luftiger.bits.backend.auth.internal.SessionStore
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars", "bits.auth.admin.username=admin"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, AuthTest.Fakes::class)
class AuthTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var discord: FakeDiscord

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var passwordEncoder: CountingPasswordEncoder

    @Autowired
    lateinit var environment: Environment

    @BeforeTest
    fun reset() {
        jdbc.execute("TRUNCATE account_link")
        redisTemplate.keys("bits:auth:*").forEach { redisTemplate.delete(it) }
        discord.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
    }

    @Test
    fun `guild member logs in with Discord as user`() {
        val session = discordLogin().andExpect { redirectedUrl("https://bits.test/") }.sessionCookie()

        me(session).andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Luca") }
            jsonPath("$.avatarUrl") { value("https://cdn.discordapp.com/avatars/42/abc.png?size=128") }
            jsonPath("$.role") { value("USER") }
            jsonPath("$.wareraUsername") { value(null) }
            jsonPath("$.discordUser") { value(true) }
        }
        mockMvc.get("/web/admin/anything") { cookie(session) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the session keeps the member's roles from the guild`() {
        discord.memberRoles = listOf("r-congress", "r-citizen")

        val session = discordLogin().sessionCookie()

        assertEquals(listOf("r-congress", "r-citizen"), SessionTokens.user(jwtDecoder.decode(session.value)).discordRoleIds)
    }

    @Test
    fun `account outside the guild is rejected`() {
        discord.guilds = listOf(DiscordGuild(id = "999"))

        val result = discordLogin().andExpect { redirectedUrl("https://bits.test/login?error=not_member") }.andReturn()
        assertNull(result.response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `administrator permission makes an admin`() {
        // Administrator (0x8) among other permissions.
        discord.guilds = listOf(DiscordGuild(id = "100", permissions = "2147483656"))

        val session = discordLogin().sessionCookie()

        me(session).andExpect { jsonPath("$.role") { value("ADMIN") } }
        mockMvc.get("/web/admin/anything") { cookie(session) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `guild owner is an admin`() {
        discord.guilds = listOf(DiscordGuild(id = "100", owner = true))

        me(discordLogin().sessionCookie()).andExpect { jsonPath("$.role") { value("ADMIN") } }
    }

    @Test
    fun `guild beyond the first page of Discord is found`() {
        discord.guilds = (1..250).map { DiscordGuild(id = (1000 + it).toString()) } + DiscordGuild(id = "100", owner = true)

        me(discordLogin().sessionCookie()).andExpect { jsonPath("$.role") { value("ADMIN") } }
        assertEquals(listOf(null, "1200"), discord.requestedPages)
    }

    @Test
    fun `unreachable Discord ends on the login page`() {
        discord.down = true

        discordLogin().andExpect { redirectedUrl("https://bits.test/login?error=discord_unavailable") }
    }

    @Test
    fun `linked WarEra account is shown`() {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES ('42', 'w1', 'Luftiger', now())",
        )

        me(discordLogin().sessionCookie()).andExpect { jsonPath("$.wareraUsername") { value("Luftiger") } }
    }

    @Test
    fun `admin logs in with username and password`() {
        mockMvc.get("/auth/options").andExpect { jsonPath("$.adminLogin") { value(true) } }

        val session = adminLogin("admin", PASSWORD).andExpect { status { isNoContent() } }.sessionCookie()

        me(session).andExpect {
            jsonPath("$.name") { value("admin") }
            jsonPath("$.role") { value("ADMIN") }
            jsonPath("$.wareraUsername") { value(null) }
            jsonPath("$.discordUser") { value(false) }
        }
    }

    @Test
    fun `wrong credentials are rejected alike`() {
        adminLogin("admin", "falsch").andExpect { status { isUnauthorized() } }
        adminLogin("root", PASSWORD).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `five failures lock the IP out for 15 minutes from the first one`() {
        adminLogin("admin", "falsch").andExpect { status { isUnauthorized() } }
        redisTemplate.expire("bits:auth:admin-login-attempts:10.0.0.1", Duration.ofMinutes(10))
        repeat(4) { adminLogin("admin", "falsch").andExpect { status { isUnauthorized() } } }

        adminLogin("admin", PASSWORD).andExpect { status { isTooManyRequests() } }
        adminLogin("admin", PASSWORD, ip = "10.0.0.2").andExpect { status { isNoContent() } }
        // Further attempts do not prolong the window.
        val ttl = redisTemplate.getExpire("bits:auth:admin-login-attempts:10.0.0.1")
        assertTrue(ttl in 9 * 60..10 * 60, "TTL was $ttl")
    }

    @Test
    fun `parallel attempts do not get past the lockout`() {
        passwordEncoder.checks.set(0)
        val pool = Executors.newFixedThreadPool(20)
        val start = CountDownLatch(1)
        val statuses =
            (1..20)
                .map {
                    pool.submit<Int> {
                        start.await()
                        adminLogin("admin", "falsch").andReturn().response.status
                    }
                }.also { start.countDown() }
                .map { it.get() }
        pool.shutdown()

        assertEquals(5, passwordEncoder.checks.get())
        assertEquals(mapOf(401 to 5, 429 to 15), statuses.groupingBy { it }.eachCount())
    }

    @Test
    fun `too many attempts from all addresses lock the admin login for everybody`() {
        redisTemplate.opsForValue().set("bits:auth:admin-login-attempts", "49", Duration.ofMinutes(15))

        adminLogin("admin", "falsch", ip = "10.0.0.3").andExpect { status { isUnauthorized() } }
        adminLogin("admin", PASSWORD, ip = "10.0.0.4").andExpect { status { isTooManyRequests() } }
        assertTrue(redisTemplate.getExpire("bits:auth:admin-login-attempts") > 0)
    }

    @Test
    fun `successful login resets the failures`() {
        repeat(4) { adminLogin("admin", "falsch") }
        adminLogin("admin", PASSWORD).andExpect { status { isNoContent() } }

        repeat(4) { adminLogin("admin", "falsch") }
        adminLogin("admin", PASSWORD).andExpect { status { isNoContent() } }
    }

    @Test
    fun `session cookie is HttpOnly, Secure and SameSite Strict for the 7 days a session can last`() {
        val userHeader = discordLogin().andReturn().response.getHeader(HttpHeaders.SET_COOKIE)!!
        val adminHeader = adminLogin("admin", PASSWORD).andReturn().response.getHeader(HttpHeaders.SET_COOKIE)!!

        for ((header, maxAge) in listOf(userHeader to "Max-Age=604800", adminHeader to "Max-Age=604800")) {
            assertTrue(header.startsWith("bits_session="), header)
            listOf("Path=/", maxAge, "Secure", "HttpOnly", "SameSite=Strict").forEach {
                assertTrue(it in header.split("; "), "$it missing in $header")
            }
        }
    }

    @Test
    fun `an active session is renewed beyond the hour`() {
        val browser = Browser(discordLogin().sessionCookie())

        repeat(6) {
            clock.advance(Duration.ofMinutes(50))
            browser.me().andExpect { status { isOk() } }
        }
        // Renewed at 100, 200 and 300 minutes, each time with the refresh token issued by the renewal before.
        assertEquals(3, discord.refreshes.get())
    }

    @Test
    fun `a renewed token is valid for an hour and the cookie for the rest of the session`() {
        val browser = Browser(discordLogin().sessionCookie())
        clock.advance(Duration.ofHours(2))

        val header =
            browser
                .me()
                .andReturn()
                .response
                .getHeader(HttpHeaders.SET_COOKIE)!!

        assertTrue("Max-Age=597600" in header.split("; "), header)
        assertEquals(clock.now + Duration.ofHours(1), jwtDecoder.decode(browser.cookie.value).expiresAt)
    }

    @Test
    fun `a session ends after 24 hours without requests`() {
        val session = discordLogin().sessionCookie()
        clock.advance(Duration.ofHours(24))
        Browser(session).me().andExpect { status { isOk() } }

        val idle = discordLogin().sessionCookie()
        clock.advance(Duration.ofHours(24).plusSeconds(1))
        Browser(idle).me().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a session ends 7 days after the login however active it is`() {
        val browser = Browser(discordLogin().sessionCookie())

        repeat(8) {
            clock.advance(Duration.ofHours(20))
            browser.me().andExpect { status { isOk() } }
        }
        clock.advance(Duration.ofHours(8))
        browser.me().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `losing the Administrator permission makes the session USER within an hour`() {
        discord.guilds = listOf(DiscordGuild(id = "100", owner = true))
        val browser = Browser(discordLogin().sessionCookie())
        discord.guilds = listOf(DiscordGuild(id = "100", permissions = "0"))

        clock.advance(Duration.ofMinutes(59))
        browser.me().andExpect { jsonPath("$.role") { value("ADMIN") } }
        clock.advance(Duration.ofMinutes(2))
        browser.me().andExpect { jsonPath("$.role") { value("USER") } }
        mockMvc.get("/web/admin/anything") { cookie(browser.cookie) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the Discord roles are checked again at the renewal`() {
        discord.memberRoles = listOf("r-congress")
        val browser = Browser(discordLogin().sessionCookie())
        discord.memberRoles = listOf("r-citizen")

        clock.advance(Duration.ofMinutes(61))
        browser.me().andExpect { status { isOk() } }

        assertEquals(listOf("r-citizen"), SessionTokens.user(jwtDecoder.decode(browser.cookie.value)).discordRoleIds)
    }

    @Test
    fun `leaving the guild ends the session within an hour`() {
        val browser = Browser(discordLogin().sessionCookie())
        discord.guilds = listOf(DiscordGuild(id = "7"))

        clock.advance(Duration.ofMinutes(61))
        browser.me().andExpect { status { isUnauthorized() } }
        assertEquals("", browser.cookie.value)
        assertTrue(redisTemplate.keys("bits:auth:session:*").isEmpty())
    }

    @Test
    fun `deauthorizing the app in Discord ends the session within an hour`() {
        val session = discordLogin().sessionCookie()
        discord.revoked = true

        clock.advance(Duration.ofMinutes(61))
        Browser(session).me().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the session goes on with the last role while Discord is down`() {
        discord.guilds = listOf(DiscordGuild(id = "100", owner = true))
        val browser = Browser(discordLogin().sessionCookie())
        discord.down = true

        clock.advance(Duration.ofMinutes(61))
        browser.me().andExpect { jsonPath("$.role") { value("ADMIN") } }
        // Tried again a minute later, not on every request.
        clock.advance(Duration.ofSeconds(30))
        browser.me().andExpect { status { isOk() } }
        clock.advance(Duration.ofSeconds(31))
        discord.down = false
        discord.guilds = listOf(DiscordGuild(id = "100", permissions = "0"))
        browser.me().andExpect { jsonPath("$.role") { value("USER") } }
        assertEquals(1, discord.refreshes.get())
    }

    @Test
    fun `a session ends after 24 hours without requests even while Discord is down`() {
        val browser = Browser(discordLogin().sessionCookie())
        discord.down = true

        clock.advance(Duration.ofHours(20))
        browser.me().andExpect { status { isOk() } }
        clock.advance(Duration.ofHours(24).plusSeconds(1))
        browser.me().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `parallel requests with an expired token check Discord once`() {
        val session = discordLogin().sessionCookie()
        clock.advance(Duration.ofMinutes(61))

        val pool = Executors.newFixedThreadPool(10)
        val start = CountDownLatch(1)
        val statuses =
            (1..10)
                .map { pool.submit<Int> { start.await().let { me(session).andReturn().response.status } } }
                .also { start.countDown() }
                .map { it.get() }
        pool.shutdown()

        assertEquals(List(10) { 200 }, statuses)
        assertEquals(1, discord.refreshes.get())
    }

    @Test
    fun `the refresh token is kept in Redis only encrypted`() {
        discordLogin().sessionCookie()

        val stored = redisTemplate.opsForHash<String, String>().get(redisTemplate.keys("bits:auth:session:*").single(), "data")!!
        assertTrue("refreshToken" in stored, stored)
        assertTrue(REFRESH_TOKEN !in stored, stored)
    }

    @Test
    fun `the admin account is renewed without Discord`() {
        val browser = Browser(adminLogin("admin", PASSWORD).sessionCookie())

        repeat(6) {
            clock.advance(Duration.ofMinutes(50))
            browser.me().andExpect { jsonPath("$.role") { value("ADMIN") } }
        }
        assertEquals(0, discord.refreshes.get())
    }

    @Test
    fun `a new admin password hash ends the sessions of the admin account`() {
        val session = adminLogin("admin", PASSWORD).sessionCookie()
        // As if the session had been started with another hash than the one configured now.
        val key = redisTemplate.keys("bits:auth:session:*").single()
        val data = redisTemplate.opsForHash<String, String>().get(key, "data")!!
        redisTemplate.opsForHash<String, String>().put(
            key,
            "data",
            data.replace(Regex(""""adminFingerprint":"[^"]+""""), """"adminFingerprint":"old""""),
        )

        clock.advance(Duration.ofMinutes(61))
        Browser(session).me().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `without Redis the session is rejected`() {
        val session = adminLogin("admin", PASSWORD).sessionCookie()
        val store = SessionStore(brokenRedis(), clock)

        assertTrue(store.validate(jwtDecoder.decode(session.value)).hasErrors())
    }

    @Test
    fun `tampered session is rejected`() {
        val token = adminLogin("admin", PASSWORD).sessionCookie().value
        val (header, payload, signature) = token.split(".")
        // Same token with the role claim edited; the signature no longer matches.
        val decoded = String(Base64.getUrlDecoder().decode(payload))
        val forged = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded.replace("admin", "other").toByteArray())

        me(Cookie("bits_session", "$header.$forged.$signature")).andExpect { status { isUnauthorized() } }
        me(Cookie("bits_session", "$header.$payload.x$signature")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `web endpoints require a session`() {
        mockMvc.get("/web/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout clears the cookie`() {
        val header =
            mockMvc
                .post(
                    "/auth/logout",
                ).andExpect { status { isNoContent() } }
                .andReturn()
                .response
                .getHeader(HttpHeaders.SET_COOKIE)!!

        assertTrue(header.startsWith("bits_session=;"), header)
        assertTrue("Max-Age=0" in header.split("; "), header)
    }

    @Test
    fun `logout ends the session for copies of the cookie too`() {
        val session = adminLogin("admin", PASSWORD).sessionCookie()
        clock.advance(Duration.ofMinutes(20))

        mockMvc.post("/auth/logout") { cookie(session) }.andExpect { status { isNoContent() } }

        me(session).andExpect { status { isUnauthorized() } }
        clock.advance(Duration.ofMinutes(41))
        Browser(session).me().andExpect { status { isUnauthorized() } }
        assertTrue(redisTemplate.keys("bits:auth:session:*").isEmpty())
        val key = redisTemplate.keys("bits:auth:revoked-session:*").single()
        val ttl = redisTemplate.getExpire(key)
        assertTrue(ttl in 39 * 60..40 * 60, "TTL was $ttl")
    }

    @Test
    fun `requests from another origin cannot change state`() {
        val session = adminLogin("admin", PASSWORD).sessionCookie()

        mockMvc
            .post("/web/admin/role-sync/rules") {
                cookie(session)
                header(HttpHeaders.ORIGIN, "https://other.bits.test")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleId":"1","blocks":[[{"type":"CITIZEN","countryId":"de"}]]}"""
            }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/auth/logout") {
                cookie(session)
                header("Sec-Fetch-Site", "same-site")
            }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/auth/login") {
                header(HttpHeaders.ORIGIN, "null")
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"admin","password":"$PASSWORD"}"""
            }.andExpect { status { isForbidden() } }
        me(session).andExpect { status { isOk() } }

        mockMvc
            .post("/auth/logout") {
                cookie(session)
                header(HttpHeaders.ORIGIN, "https://bits.test")
                header("Sec-Fetch-Site", "same-origin")
            }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `paths outside the three prefixes are denied`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/actuator/env").andExpect { status { isForbidden() } }
        mockMvc.get("/admin/role-sync/rules").andExpect { status { isForbidden() } }
        mockMvc.post("/anything").andExpect { status { isForbidden() } }
    }

    @Test
    fun `discord login uses PKCE and a short-lived secure session`() {
        val location =
            mockMvc
                .get("/auth/oauth2/authorization/discord")
                .andReturn()
                .response.redirectedUrl!!
        val query = UriComponentsBuilder.fromUriString(location).build().queryParams

        assertNotNull(query.getFirst("code_challenge"))
        assertEquals("S256", query.getFirst("code_challenge_method"))
        assertEquals("true", environment.getProperty("server.servlet.session.cookie.secure"))
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"))
        assertEquals("10m", environment.getProperty("server.servlet.session.timeout"))
    }

    @Test
    fun `session cookie does not open the bot API`() {
        val session = adminLogin("admin", PASSWORD).sessionCookie()

        mockMvc
            .post("/api/ping") {
                cookie(session)
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"42"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    /** Walks through the authorization code flow: to Discord and back with a code. */
    private fun discordLogin(): ResultActionsDsl {
        val start =
            mockMvc
                .get("/auth/oauth2/authorization/discord")
                .andExpect { status { is3xxRedirection() } }
                .andReturn()
        val location = UriComponentsBuilder.fromUriString(start.response.redirectedUrl!!).build()
        assertEquals("discord.com", location.host)
        assertEquals("https://bits.test/auth/oauth2/callback/discord", location.queryParams.getFirst("redirect_uri")?.let(::urlDecode))
        assertNotNull(location.queryParams.getFirst("code_challenge"))
        assertEquals("identify guilds guilds.members.read", location.queryParams.getFirst("scope")?.let(::urlDecode))

        return mockMvc.get("/auth/oauth2/callback/discord") {
            session = start.request.session as MockHttpSession
            param("code", "code-from-discord")
            param("state", urlDecode(location.queryParams.getFirst("state")!!))
        }
    }

    private fun adminLogin(
        username: String,
        password: String,
        ip: String = "10.0.0.1",
    ) = mockMvc.post("/auth/login") {
        with { it.apply { remoteAddr = ip } }
        contentType = MediaType.APPLICATION_JSON
        content = """{"username":"$username","password":"$password"}"""
    }

    private fun me(session: Cookie) = mockMvc.get("/web/me") { cookie(session) }

    /** Keeps the session cookie the way a browser does, taking over what the responses set. */
    private inner class Browser(
        var cookie: Cookie,
    ) {
        fun me(): ResultActionsDsl =
            me(cookie).also { result ->
                result
                    .andReturn()
                    .response
                    .getCookie(SessionTokens.COOKIE_NAME)
                    ?.let { cookie = Cookie(it.name, it.value) }
            }
    }

    /** A Redis that cannot be reached: the connection fails on every call. */
    private fun brokenRedis() =
        StringRedisTemplate(
            LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", 1)).apply {
                afterPropertiesSet()
                start()
            },
        )

    private fun ResultActionsDsl.sessionCookie(): Cookie {
        val cookie = andReturn().response.getCookie("bits_session")
        assertNotNull(cookie, "no session cookie set")
        return Cookie(cookie.name, cookie.value)
    }

    private fun urlDecode(value: String) = URLDecoder.decode(value, Charsets.UTF_8)

    /** Discord as seen with the access token of user 42, who is a plain member of guild 100. */
    class FakeDiscord : DiscordApi {
        var guilds = emptyList<DiscordGuild>()
        var memberRoles = emptyList<String>()
        var down = false
        var revoked = false
        val requestedPages = mutableListOf<String?>()

        /** The only refresh token Discord accepts; each refresh spends it and issues the next one. */
        var refreshToken = REFRESH_TOKEN
        val refreshes = AtomicInteger()

        fun reset() {
            guilds = listOf(DiscordGuild(id = "7"), DiscordGuild(id = "100", permissions = "0"))
            memberRoles = emptyList()
            down = false
            revoked = false
            requestedPages.clear()
            refreshToken = REFRESH_TOKEN
            refreshes.set(0)
        }

        @Synchronized
        override fun refreshTokens(refreshToken: String): DiscordTokens {
            if (down) throw ResourceAccessException("down")
            if (revoked || refreshToken != this.refreshToken) throw DiscordGrantRevokedException("invalid_grant")
            this.refreshToken = "refresh-${refreshes.incrementAndGet()}"
            return DiscordTokens(ACCESS_TOKEN, this.refreshToken)
        }

        override fun getCurrentUser(accessToken: String) =
            available(accessToken) { DiscordUser(id = "42", username = "luca", globalName = "Luca", avatar = "abc") }

        override fun getCurrentUserGuildMember(
            accessToken: String,
            guildId: String,
        ) = available(accessToken) { DiscordGuildMember(roles = memberRoles) }

        // Paged like Discord, in the order of [guilds] instead of by ID.
        override fun getCurrentUserGuilds(
            accessToken: String,
            after: String?,
            limit: Int,
        ) = available(accessToken) {
            requestedPages += after
            guilds.drop(guilds.indexOfFirst { it.id == after } + 1).take(limit)
        }

        private fun <T> available(
            accessToken: String,
            block: () -> T,
        ): T {
            assertEquals(ACCESS_TOKEN, accessToken)
            if (down) throw ResourceAccessException("down")
            return block()
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fakes {
        @Bean
        @Primary
        fun fakeDiscordApi() = FakeDiscord()

        /** Stands in for Discord's token endpoint and accepts any code. */
        @Bean
        @Primary
        fun fakeTokenResponseClient() =
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
                OAuth2AccessTokenResponse
                    .withToken(ACCESS_TOKEN)
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(3600)
                    .refreshToken(REFRESH_TOKEN)
                    .build()
            }

        @Bean
        @Primary
        fun mutableClock() = MutableClock()

        @Bean
        @Primary
        fun countingPasswordEncoder() = CountingPasswordEncoder()
    }

    class CountingPasswordEncoder : PasswordEncoder by BCryptPasswordEncoder() {
        val checks = AtomicInteger()

        override fun matches(
            rawPassword: CharSequence?,
            encodedPassword: String?,
        ): Boolean {
            checks.incrementAndGet()
            return BCryptPasswordEncoder().matches(rawPassword, encodedPassword)
        }
    }

    companion object {
        const val PASSWORD = "richtig-geheim"
        const val ACCESS_TOKEN = "discord-access-token"
        const val REFRESH_TOKEN = "discord-refresh-token"

        @JvmStatic
        @DynamicPropertySource
        fun adminPassword(registry: DynamicPropertyRegistry) {
            registry.add("bits.auth.admin.password-hash") { BCryptPasswordEncoder(10).encode(PASSWORD)!! }
        }
    }
}
