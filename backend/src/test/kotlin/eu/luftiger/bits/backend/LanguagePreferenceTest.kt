package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.members.internal.GuildMemberService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class LanguagePreferenceTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var memberService: GuildMemberService

    @BeforeTest
    fun reset() {
        jdbc.execute("TRUNCATE account_link, language_preference")
        memberService.forget()
    }

    @Test
    fun `a member without a link stores a language, changes it and goes back to automatic`() {
        assertLanguage("1", null)

        set("1", """"ENGLISH"""")
        assertLanguage("1", "ENGLISH")

        set("1", """"GERMAN"""")
        assertLanguage("1", "GERMAN")

        set("1", "null")
        assertLanguage("1", null)
        assertEquals(0, count())
    }

    @Test
    fun `going back to automatic without a choice changes nothing`() {
        set("1", "null")

        assertEquals(0, count())
    }

    @Test
    fun `needs the service token and a valid Discord ID`() {
        mockMvc.get("/api/language-preferences/1").andExpect { status { isUnauthorized() } }
        mockMvc
            .get("/api/language-preferences/not-an-id") { header("Authorization", TOKEN) }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `leaving the server deletes the language`() {
        set("1", """"ENGLISH"""")

        mockMvc
            .post("/api/discord-members/left") {
                header("Authorization", TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"1","memberName":"Luca"}"""
            }.andExpect { status { isNoContent() } }

        assertEquals(0, count())
    }

    @Test
    fun `the member list deletes the language of somebody without a link who left meanwhile`() {
        set("1", """"ENGLISH"""")
        set("2", """"ENGLISH"""")

        memberService.replaceAll(setOf("2", "3"))

        assertLanguage("1", null)
        assertLanguage("2", "ENGLISH")
    }

    @Test
    fun `an empty member list deletes no language`() {
        set("1", """"ENGLISH"""")

        memberService.replaceAll(emptySet())

        assertLanguage("1", "ENGLISH")
    }

    private fun set(
        discordUserId: String,
        languageJson: String,
    ) {
        mockMvc
            .post("/api/language-preferences") {
                header("Authorization", TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"$discordUserId","language":$languageJson}"""
            }.andExpect { status { isOk() } }
    }

    private fun assertLanguage(
        discordUserId: String,
        expected: String?,
    ) {
        mockMvc
            .get("/api/language-preferences/$discordUserId") { header("Authorization", TOKEN) }
            .andExpect {
                status { isOk() }
                if (expected == null) {
                    jsonPath("$.language") { doesNotExist() }
                } else {
                    jsonPath("$.language") { value(expected) }
                }
            }
    }

    private fun count() = jdbc.queryForObject("SELECT COUNT(*) FROM language_preference", Int::class.java)

    private companion object {
        const val TOKEN = "Bearer test-service-token-at-least-32-chars"
    }
}
