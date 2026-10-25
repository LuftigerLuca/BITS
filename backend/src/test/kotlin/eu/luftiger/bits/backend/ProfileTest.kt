package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.ProfileResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=$SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class ProfileTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.execute("TRUNCATE account_link, event_publication")
        warera.reset()
    }

    @Test
    fun `profile of a linked account comes from WarEra`() {
        link("1", "w1", linkedAs = "OldName")

        assertEquals(
            ProfileResponse.Found(
                wareraUserId = "w1",
                wareraUsername = "Luftiger",
                level = 30,
                militaryRank = 40,
                countryName = "Germany",
                militaryUnitName = "Deutsche Marine",
                profileUrl = "https://app.warera.io/user/w1",
            ),
            profile("1"),
        )
    }

    @Test
    fun `profile without a military unit or with an unknown one leaves it out`() {
        link("1", "w2")
        link("2", "w3")
        warera.users["w3"] = warera.users.getValue("w1").copy(id = "w3", country = "xx", mu = "gone")

        val withoutUnit = profile("1") as ProfileResponse.Found
        val unknownUnit = profile("2") as ProfileResponse.Found

        assertEquals("France" to null, withoutUnit.countryName to withoutUnit.militaryUnitName)
        assertEquals(null to null, unknownUnit.countryName to unknownUnit.militaryUnitName)
    }

    @Test
    fun `unlinked account and deleted WarEra account are outcomes`() {
        link("2", "w2")
        warera.deletedUsers += "w2"

        assertEquals(ProfileResponse.NotLinked, profile("1"))
        assertEquals(ProfileResponse.WareraUserNotFound, profile("2"))
    }

    @Test
    fun `unreachable WarEra is a 503`() {
        link("1", "w1")
        warera.down = true

        request("1").andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `profiles need the service token and a valid ID`() {
        mockMvc
            .post("/api/profiles") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"1"}"""
            }.andExpect { status { isUnauthorized() } }
        request("not-an-id").andExpect { status { isBadRequest() } }
    }

    private fun profile(discordUserId: String): ProfileResponse =
        BitsJson.decodeFromString(
            request(discordUserId)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun request(discordUserId: String) =
        mockMvc.post("/api/profiles") {
            header("Authorization", "Bearer $SERVICE_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"discordUserId":"$discordUserId"}"""
        }

    private fun link(
        discordUserId: String,
        wareraUserId: String,
        linkedAs: String = wareraUserId,
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, now())",
            discordUserId,
            wareraUserId,
            linkedAs,
        )
    }
}

private const val SERVICE_TOKEN = "test-service-token-at-least-32-chars"
