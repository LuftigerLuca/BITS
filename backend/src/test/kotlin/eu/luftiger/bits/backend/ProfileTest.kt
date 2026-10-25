package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.ProfileDetail
import eu.luftiger.bits.shared.ProfileParty
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.warera.WareraParty
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
        jdbc.execute("TRUNCATE account_link, role_rule, discord_role, event_publication")
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
                companyCount = 1,
            ),
            profile("1"),
        )
    }

    @Test
    fun `profile shows the party in the country of citizenship and whether the user leads it`() {
        link("1", "w1")
        link("2", "w2")
        link("3", "w3")
        warera.users["w3"] = warera.users.getValue("w1").copy(id = "w3")
        // w2 is a citizen of France, but listed in a German party only.
        warera.parties["de"] =
            listOf(
                WareraParty("p-de", "Deutsche Partei", listOf("w1", "w3"), leader = "w1"),
                WareraParty("p-other", "Andere", listOf("w2")),
            )
        warera.parties["fr"] = listOf(WareraParty("p-fr", "Parti", listOf("w9")))

        assertEquals(ProfileParty("Deutsche Partei", leader = true), (profile("1") as ProfileResponse.Found).party)
        assertEquals(ProfileParty("Deutsche Partei", leader = false), (profile("3") as ProfileResponse.Found).party)
        assertEquals(null, (profile("2") as ProfileResponse.Found).party)
    }

    @Test
    fun `profile shows the offices in the government of the country of citizenship`() {
        link("1", "w1")
        link("2", "w2")
        warera.users["w2"] = warera.users.getValue("w2").copy(country = "de")

        assertEquals(emptyList(), (profile("1") as ProfileResponse.Found).offices)
        assertEquals(listOf("Präsident"), (profile("2") as ProfileResponse.Found).offices)
    }

    @Test
    fun `profile lists the managed roles, highest first`() {
        link("1", "w1")
        jdbc.update(
            "INSERT INTO discord_role (id, name, color, position, assignable) VALUES " +
                "('r-low', 'Low', NULL, 1, TRUE), ('r-high', 'High', NULL, 5, TRUE), " +
                "('r-unmanaged', 'Unmanaged', NULL, 3, TRUE), ('r-unassignable', 'Above the bot', NULL, 9, FALSE)",
        )
        jdbc.update(
            "INSERT INTO role_rule (role_id, type, threshold, created_at) VALUES " +
                "('r-low', 'LEVEL', 1, now()), ('r-high', 'LEVEL', 10, now()), ('r-high', 'MILITARY_RANK', 10, now()), " +
                "('r-unassignable', 'LEVEL', 1, now()), ('r-deleted', 'LEVEL', 1, now())",
        )

        assertEquals(listOf("r-high", "r-low"), (profile("1") as ProfileResponse.Found).managedRoleIds)
    }

    @Test
    fun `profile comes without the details WarEra cannot deliver`() {
        link("1", "w1")
        warera.partiesDown = true
        warera.governmentDown = true
        warera.companiesDown = true

        val profile = profile("1") as ProfileResponse.Found

        assertEquals("Luftiger", profile.wareraUsername)
        assertEquals(setOf(ProfileDetail.PARTY, ProfileDetail.OFFICES, ProfileDetail.COMPANIES), profile.unavailable)
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
