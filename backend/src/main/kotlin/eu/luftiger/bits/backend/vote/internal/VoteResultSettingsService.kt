package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.overview.api.OverviewCountry
import eu.luftiger.bits.backend.vote.domain.PartyColor
import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.domain.VoteResultSetting
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraParty
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The background and the party colours of the result image, which admins set under "Verwaltung". Colours are kept
 * by WarEra's party ID together with the country the party belongs to, and only those of the country set under
 * "Verwaltung" count, so the parties of other countries keep their palette colour, also after the country changed.
 *
 * Every image is drawn with the settings of the moment, so they apply from the next evaluation on and also to the
 * archive in the dashboard. The decoded background is kept in memory, since there is only one backend (ADR 0005).
 */
@Service
class VoteResultSettingsService(
    private val settingRepository: VoteResultSettingRepository,
    private val partyColorRepository: PartyColorRepository,
    private val overviewCountry: OverviewCountry,
    private val wareraClient: WareraClient,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** The decoded background, `null` until the first image after a change. */
    @Volatile
    private var cachedBackground: BufferedImage? = null

    fun settings(): ResultSettings {
        val setting = settingRepository.findByIdOrNull(VoteResultSetting.SINGLE_ROW_ID)
        return ResultSettings(customBackground = setting?.background != null, updatedAt = setting?.updatedAt)
    }

    /** What the next image is drawn with. */
    fun style(): VoteResultStyle {
        val countryId = overviewCountry.selected()?.id
        return VoteResultStyle(
            background = background(),
            partyColors =
                countryId
                    ?.let(partyColorRepository::findByCountryId)
                    .orEmpty()
                    .associate { it.partyId to Color(it.color) },
            noPartyColor = settingRepository.noPartyColor()?.let(::Color) ?: VotePalette.noParty,
        )
    }

    private fun background(): BufferedImage =
        cachedBackground ?: (settingRepository.background()?.let(::decode) ?: VoteBackground.default).also { cachedBackground = it }

    /**
     * Stores [upload] as the new background, scaled down to what an image needs and as JPEG, so neither its size nor
     * its metadata end up in the database.
     *
     * @throws ResponseStatusException 400 if it is no PNG or JPEG BITS can read, or larger than [MAX_PIXELS]
     */
    fun setBackground(
        upload: ByteArray,
        actor: AuditActor,
    ) {
        val image = normalize(read(upload))
        val jpeg = encode(image)
        change(actor, AuditAction.VOTE_RESULT_BACKGROUND_CHANGED, "Hintergrund der Auswertung: eigenes Bild") {
            it.background = jpeg
        }
        log.info("Background of the result image set: {}×{}, {} bytes", image.width, image.height, jpeg.size)
    }

    fun resetBackground(actor: AuditActor) {
        change(actor, AuditAction.VOTE_RESULT_BACKGROUND_CHANGED, "Hintergrund der Auswertung: BITS-Adler") {
            it.background = null
        }
    }

    /**
     * The parties of the country under "Verwaltung" with their colour.
     *
     * @throws WareraApiException if WarEra cannot be reached
     */
    fun partyColors(): PartyColors {
        val setting = settingRepository.findByIdOrNull(VoteResultSetting.SINGLE_ROW_ID)
        val country = overviewCountry.selected() ?: return PartyColors(null, emptyList(), setting?.noPartyColor)
        val colors = partyColorRepository.findByCountryId(country.id).associate { it.partyId to it.color }
        val parties = sorted(wareraClient.getParties(country.id)).map { PartyColors.Party(it.id, it.name, colors[it.id]) }
        return PartyColors(country.name, parties, setting?.noPartyColor)
    }

    /**
     * Replaces the colours of the parties of the country under "Verwaltung" with [colors] (0xRRGGBB by party ID);
     * parties left out get the palette's again.
     *
     * @throws ResponseStatusException 409 if no country is set, 400 for parties not in it
     * @throws WareraApiException if WarEra cannot be reached
     */
    fun setPartyColors(
        colors: Map<String, Int>,
        noPartyColor: Int?,
        actor: AuditActor,
    ) {
        val country = overviewCountry.selected() ?: throw ResponseStatusException(HttpStatus.CONFLICT, "No country set")
        val parties = wareraClient.getParties(country.id).associateBy { it.id }
        val unknown = colors.keys.filter { it !in parties }
        if (unknown.isNotEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown parties $unknown")
        val described =
            sorted(parties.values).mapNotNull { party -> colors[party.id]?.let { "${party.name} ${hex(it)}" } } +
                listOfNotNull(noPartyColor?.let { "${VoteResultRenderer.NO_PARTY} ${hex(it)}" })
        val description =
            "Parteifarben (${country.name}): " + if (described.isEmpty()) "alle aus der Palette" else described.joinToString()
        change(actor, AuditAction.VOTE_PARTY_COLORS_CHANGED, description) { setting ->
            partyColorRepository.deleteByCountryId(country.id)
            partyColorRepository.saveAll(colors.map { (partyId, color) -> PartyColor(partyId, country.id, color) })
            setting.noPartyColor = noPartyColor
        }
    }

    /**
     * An image with made-up numbers, drawn like a real one: with the largest parties of the country under
     * "Verwaltung", or with invented ones if there is none or WarEra cannot be reached.
     */
    fun preview(): ByteArray {
        val country = overviewCountry.selected()
        val parties =
            country?.let {
                try {
                    sorted(wareraClient.getParties(it.id)).take(SAMPLE_COUNTS.size)
                } catch (e: WareraApiException) {
                    log.warn("WarEra API not reachable, the preview shows invented parties", e)
                    null
                }
            } ?: (1..SAMPLE_COUNTS.size).map { WareraParty("", "Partei $it") }
        val results =
            parties.mapIndexed { index, party ->
                val (yes, no, abstain, eligible) = SAMPLE_COUNTS[index]
                PartyResult(0, index, party.name, yes, no, abstain, eligible, party.id.ifEmpty { null })
            } + PartyResult(0, parties.size, null, yes = 2, no = 1, abstain = 1, eligible = 6)
        val result = VoteResult("Soll der Kongress den Haushalt beschließen?", results, listOf("Anna", "Ben", "Clara", "David"))
        return VoteResultRenderer.png(result, style())
    }

    private fun change(
        actor: AuditActor,
        action: AuditAction,
        description: String,
        update: (VoteResultSetting) -> Unit,
    ) {
        transactionTemplate.executeWithoutResult {
            val setting =
                settingRepository.findByIdOrNull(VoteResultSetting.SINGLE_ROW_ID)
                    ?: settingRepository.save(VoteResultSetting(clock.instant()))
            update(setting)
            setting.updatedAt = clock.instant()
            auditLog.record(actor, action, description)
        }
        cachedBackground = null
    }

    /** Largest first, like they mostly come out of an evaluation. */
    private fun sorted(parties: Collection<WareraParty>) =
        parties.sortedWith(compareByDescending<WareraParty> { it.members.size }.thenBy { it.name })

    private fun hex(color: Int) = "#%06X".format(color)

    /** Checks format and size before decoding, so a small file cannot unpack into a huge image. */
    private fun read(upload: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(upload)) ?: throw invalid("No image")
        input.use {
            val reader =
                ImageIO.getImageReaders(input).asSequence().firstOrNull { it.formatName.lowercase() in FORMATS }
                    ?: throw invalid("Neither PNG nor JPEG")
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width.toLong() * height > MAX_PIXELS) throw invalid("Image too large: $width×$height")
                return reader.read(0)
            } catch (e: IOException) {
                throw invalid("Unreadable image: ${e.message}")
            } finally {
                reader.dispose()
            }
        }
    }

    /** Opaque and at most [MAX_SIDE] on its longer side, which is plenty for an image 1100 px wide. */
    private fun normalize(source: BufferedImage): BufferedImage {
        val scale = min(1.0, MAX_SIDE.toDouble() / max(source.width, source.height))
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            // Transparent parts turn black, like the darkening does anyway.
            g.color = Color.BLACK
            g.fillRect(0, 0, width, height)
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun encode(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        return try {
            ByteArrayOutputStream().use { out ->
                ImageIO.createImageOutputStream(out).use { output ->
                    writer.output = output
                    val param =
                        writer.defaultWriteParam.apply {
                            compressionMode = ImageWriteParam.MODE_EXPLICIT
                            compressionQuality = 0.88f
                        }
                    writer.write(null, IIOImage(image, null, null), param)
                }
                out.toByteArray()
            }
        } finally {
            writer.dispose()
        }
    }

    /** Stored by [setBackground], so it can be read; if not, the eagle is better than no image. */
    private fun decode(jpeg: ByteArray): BufferedImage? =
        try {
            ImageIO.read(ByteArrayInputStream(jpeg))
        } catch (e: IOException) {
            log.error("Stored background of the result image is unreadable, using the eagle", e)
            null
        }

    private fun invalid(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private companion object {
        val FORMATS = setOf("png", "jpeg", "jpg")
        const val MAX_PIXELS = 40_000_000L
        const val MAX_SIDE = 2400

        /** Yes, no, abstain and eligible of the sample parties, largest first. */
        val SAMPLE_COUNTS = listOf(listOf(9, 3, 1, 15), listOf(4, 5, 0, 10), listOf(2, 3, 2, 8), listOf(1, 2, 0, 4))
    }
}

class ResultSettings(
    val customBackground: Boolean,
    /** `null` while nothing was changed. */
    val updatedAt: Instant?,
)

class PartyColors(
    /** `null` while no country is set under "Verwaltung". */
    val countryName: String?,
    val parties: List<Party>,
    /** 0xRRGGBB, `null` for the palette's. */
    val noPartyColor: Int?,
) {
    class Party(
        val id: String,
        val name: String,
        /** 0xRRGGBB, `null` for the palette's. */
        val color: Int?,
    )
}
