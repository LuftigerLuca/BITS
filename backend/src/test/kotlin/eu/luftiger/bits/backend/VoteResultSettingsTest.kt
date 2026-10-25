package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.vote.internal.VotePalette
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.VoteResultDto
import eu.luftiger.bits.warera.WareraParty
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["bits.service-token=$TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class VoteResultSettingsTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @BeforeTest
    fun reset() {
        // Through the API, so the backend forgets the background it keeps in memory.
        adminDelete(BACKGROUND).andExpect { status { isNoContent() } }
        jdbc.execute("TRUNCATE vote, vote_result_setting, vote_party_color, overview_setting, audit_entry CASCADE")
        warera.reset()
    }

    @Test
    fun `an uploaded background shows in the preview until it is reset`() {
        adminGet(SETTINGS).andExpect { jsonPath("$.customBackground") { value(false) } }

        upload(image(Color.RED, "png"), MediaType.IMAGE_PNG).andExpect { status { isNoContent() } }

        adminGet(SETTINGS).andExpect { jsonPath("$.customBackground") { value(true) } }
        // Around the card, where the background shows: red, but darkened.
        val corner = Color(preview().getRGB(4, 4))
        assertTrue(corner.red in 60..100 && corner.green < 10 && corner.blue < 10, "corner $corner")

        adminDelete(BACKGROUND).andExpect { status { isNoContent() } }
        adminGet(SETTINGS).andExpect { jsonPath("$.customBackground") { value(false) } }
        assertTrue(Color(preview().getRGB(4, 4)).red < 60)
        assertEquals(
            listOf("Hintergrund der Auswertung: eigenes Bild", "Hintergrund der Auswertung: BITS-Adler"),
            jdbc.queryForList("SELECT description FROM audit_entry ORDER BY id", String::class.java),
        )
    }

    @Test
    fun `a JPEG is taken as well`() {
        upload(image(Color.BLUE, "jpeg"), MediaType.IMAGE_JPEG).andExpect { status { isNoContent() } }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vote_result_setting WHERE background IS NOT NULL", Int::class.java))
    }

    @Test
    fun `files that are too large, of another type or no image are rejected`() {
        upload(ByteArray(5 * 1024 * 1024 + 1), MediaType.IMAGE_PNG).andExpect { status { isPayloadTooLarge() } }
        upload("no image".toByteArray(), MediaType.IMAGE_PNG).andExpect { status { isBadRequest() } }
        upload(image(Color.RED, "gif"), MediaType.IMAGE_GIF).andExpect { status { isUnsupportedMediaType() } }
        // A GIF that claims to be a PNG.
        upload(image(Color.RED, "gif"), MediaType.IMAGE_PNG).andExpect { status { isBadRequest() } }

        adminGet(SETTINGS).andExpect { jsonPath("$.customBackground") { value(false) } }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Int::class.java))
    }

    @Test
    fun `party colours need a country`() {
        adminGet(PARTY_COLORS).andExpect {
            status { isOk() }
            jsonPath("$.countryName") { value(null) }
            jsonPath("$.parties") { isEmpty() }
        }
        setColors("""{"colors":{},"noPartyColor":null}""").andExpect { status { isConflict() } }
    }

    @Test
    fun `party colours apply to the parties of the country, the others keep the palette`() {
        setCountry()
        warera.parties["de"] =
            listOf(WareraParty("p-small", "Kleine Partei", listOf("w5")), WareraParty("p-de", "Deutsche Partei", listOf("w1", "w3")))
        warera.parties["fr"] = listOf(WareraParty("p-fr", "Parti", listOf("w2")))

        adminGet(PARTY_COLORS).andExpect {
            jsonPath("$.countryName") { value("Germany") }
            // Largest first.
            jsonPath("$.parties[0].id") { value("p-de") }
            jsonPath("$.parties[1].id") { value("p-small") }
            jsonPath("$.parties[0].color") { value(null) }
        }
        setColors("""{"colors":{"p-fr":"#FF0000"},"noPartyColor":null}""").andExpect { status { isBadRequest() } }
        setColors("""{"colors":{"p-de":"red"},"noPartyColor":null}""").andExpect { status { isBadRequest() } }
        setColors("""{"colors":{"p-de":"#ff0000"},"noPartyColor":"#00FF00"}""").andExpect { status { isNoContent() } }

        adminGet(PARTY_COLORS).andExpect {
            jsonPath("$.parties[0].color") { value("#FF0000") }
            jsonPath("$.parties[1].color") { value(null) }
            jsonPath("$.noPartyColor") { value("#00FF00") }
        }
        assertEquals(
            listOf("Parteifarben (Germany): Deutsche Partei #FF0000, Parteilos #00FF00"),
            jdbc.queryForList("SELECT description FROM audit_entry ORDER BY id", String::class.java),
        )

        // An evaluated vote with a German party, a French one and those in no party.
        val voteId = evaluatedVote("p-de" to "Deutsche Partei", "p-fr" to "Parti", null to null)
        val image = resultImage(voteId)
        assertTrue(pixels(image, Color.RED) > 2_000, "German party not in its colour")
        assertTrue(pixels(image, Color.GREEN) > 2_000, "no party not in its colour")
        assertTrue(pixels(image, VotePalette.color(1)) > 2_000, "French party not in the palette's colour")
        assertTrue(pixels(preview(), Color.RED) > 2_000, "preview without the German party's colour")

        // Leaving a party out gives it the palette's colour again.
        setColors("""{"colors":{},"noPartyColor":null}""").andExpect { status { isNoContent() } }
        assertEquals(0, pixels(resultImage(voteId), Color.RED))
    }

    @Test
    fun `colours of the parties of another country do not count`() {
        setCountry()
        warera.parties["de"] = listOf(WareraParty("p-de", "Deutsche Partei", listOf("w1")))
        setColors("""{"colors":{"p-de":"#FF0000"},"noPartyColor":null}""").andExpect { status { isNoContent() } }

        jdbc.update("UPDATE overview_setting SET country_id = 'fr', country_name = 'France'")

        assertEquals(0, pixels(resultImage(evaluatedVote("p-de" to "Deutsche Partei")), Color.RED))
    }

    @Test
    fun `without WarEra the parties cannot be listed, but the preview still shows`() {
        setCountry()
        warera.down = true

        adminGet(PARTY_COLORS).andExpect { status { isServiceUnavailable() } }
        preview()
    }

    @Test
    fun `only admins change the result image`() {
        val member = session(SessionUser.discord("7", "Member", "https://avatar", Role.USER))

        mockMvc.get(SETTINGS) { cookie(member) }.andExpect { status { isForbidden() } }
        mockMvc
            .put(BACKGROUND) {
                cookie(member)
                contentType = MediaType.IMAGE_PNG
                content = image(Color.RED, "png")
            }.andExpect { status { isForbidden() } }
    }

    private fun setCountry() {
        jdbc.update("INSERT INTO overview_setting (country_id, country_name, updated_at) VALUES ('de', 'Germany', now())")
    }

    /** One party result per entry of WarEra party ID and name, each with two yes, one no and one abstention. */
    private fun evaluatedVote(vararg parties: Pair<String?, String?>): Long {
        val voteId =
            jdbc.queryForObject(
                "INSERT INTO vote (question, role_id, channel_id, state, started_at, started_by_name, evaluated_at) " +
                    "VALUES ('Frage?', 'r', 'c', 'EVALUATED', now(), 'Admin', now()) RETURNING id",
                Long::class.java,
            )!!
        parties.forEachIndexed { index, (id, name) ->
            jdbc.update(
                "INSERT INTO vote_party_result " +
                    "(vote_id, position, party_name, party_id, yes_count, no_count, abstain_count, eligible_count) " +
                    "VALUES (?, ?, ?, ?, 2, 1, 1, 4)",
                voteId,
                index,
                name,
                id,
            )
        }
        return voteId
    }

    private fun resultImage(voteId: Long): BufferedImage {
        val result =
            BitsJson.decodeFromString<VoteResultDto>(
                mockMvc
                    .get("/api/votes/$voteId/result") { header("Authorization", "Bearer $TOKEN") }
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString,
            )
        return ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(result.image)))
    }

    private fun preview(): BufferedImage =
        ImageIO.read(
            ByteArrayInputStream(
                adminGet("$SETTINGS/preview.png")
                    .andExpect {
                        status { isOk() }
                        content { contentType(MediaType.IMAGE_PNG) }
                    }.andReturn()
                    .response.contentAsByteArray,
            ),
        )

    private fun upload(
        bytes: ByteArray,
        type: MediaType,
    ) = mockMvc.put(BACKGROUND) {
        cookie(session(SessionUser.admin("admin")))
        contentType = type
        content = bytes
    }

    private fun setColors(body: String) =
        mockMvc.put(PARTY_COLORS) {
            cookie(session(SessionUser.admin("admin")))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun adminGet(path: String) = mockMvc.get(path) { cookie(session(SessionUser.admin("admin"))) }

    private fun adminDelete(path: String) = mockMvc.delete(path) { cookie(session(SessionUser.admin("admin"))) }

    private fun image(
        color: Color,
        format: String,
    ): ByteArray {
        val image = BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().run {
            this.color = color
            fillRect(0, 0, 400, 300)
            dispose()
        }
        return ByteArrayOutputStream().use {
            ImageIO.write(image, format, it)
            it.toByteArray()
        }
    }

    private fun pixels(
        image: BufferedImage,
        color: Color,
    ): Int {
        var count = 0
        for (x in 0 until image.width) for (y in 0 until image.height) if (image.getRGB(x, y) == color.rgb) count++
        return count
    }

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }

    private companion object {
        const val SETTINGS = "/web/admin/votes/result-settings"
        const val BACKGROUND = "$SETTINGS/background"
        const val PARTY_COLORS = "$SETTINGS/party-colors"
    }
}

private const val TOKEN = "test-service-token-at-least-32-chars"
