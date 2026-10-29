package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.embassy.domain.DiscordThread
import eu.luftiger.bits.backend.embassy.domain.EmbassySetting
import eu.luftiger.bits.backend.embassy.domain.EmbassyThread
import eu.luftiger.bits.backend.embassy.domain.EmbassyThreadCreation
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.overview.api.OverviewCountry
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.vote.api.GuildChannels
import eu.luftiger.bits.shared.EmbassyThreadCreationEvent
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.ThreadCreationFailure
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCountry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

/**
 * Embassy threads the bot creates, when an admin asks for one or when a linked member holds one of the chosen offices
 * in a foreign country without a thread. A request is stored and handed to the bot as an [EmbassyThreadCreationEvent]
 * in one transaction; the bot reports the thread, which is then assigned to the country, or why it failed. Without an
 * answer within [TIMEOUT], the request fails, so the country is free again; a thread the bot reports after that is not
 * taken, and the bot deletes it.
 */
@Service
class EmbassyThreadCreationService(
    private val creationRepository: EmbassyThreadCreationRepository,
    private val threadRepository: EmbassyThreadRepository,
    private val discordThreadRepository: DiscordThreadRepository,
    private val settingRepository: EmbassySettingRepository,
    private val officeRepository: EmbassyOfficeRepository,
    private val guildChannels: GuildChannels,
    private val overviewCountry: OverviewCountry,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
    private val auditLog: AuditLog,
    private val systemLog: SystemLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Asks the bot to create the thread [threadName] for [countryId] in the embassy channel. A failed request for the
     * country is replaced.
     *
     * @throws ResponseStatusException 400 while no embassy channel is set, for a name that is blank or too long, a
     * country WarEra does not know or the country under "Verwaltung"; 409 if the country has a thread or one is being
     * created
     * @throws WareraApiException if WarEra cannot be reached, which the dashboard gets as 503
     */
    fun request(
        countryId: String,
        threadName: String,
        actor: AuditActor,
    ) {
        val name = threadName.trim()
        if (name.isEmpty() || name.length > MAX_THREAD_NAME_LENGTH) throw badRequest("Thread name must have 1 to 100 characters")
        val channelId =
            settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID)?.channelId ?: throw badRequest("No embassy channel set")
        if (countryId == overviewCountry.selected()?.id) throw badRequest("Own country $countryId")
        val country = wareraClient.getAllCountries().firstOrNull { it.id == countryId } ?: throw badRequest("Unknown country $countryId")
        create(country, name, channelId, actor, "Botschafts-Thread $name für ${country.name} vom Bot anlegen lassen")
    }

    /**
     * Asks the bot for a thread named after the template for every foreign country in which one of the users in [countryByUser]
     * (WarEra user ID to country ID) holds one of the chosen offices, and that has neither a thread nor a request, not
     * even a failed one: that waits for an admin. Does nothing while no embassy channel is set. Governments come from
     * the WarEra cache; a country whose government cannot be loaded is tried again with the next sync.
     */
    fun createMissing(countryByUser: Map<String, String>) {
        val setting = settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID) ?: return
        val channelId = setting.channelId ?: return
        val offices = officeRepository.findAll().map { it.office }.sortedBy { it.ordinal }
        val taken =
            threadRepository.findAll().mapTo(HashSet()) { it.countryId } +
                creationRepository.findAll().map { it.countryId } +
                listOfNotNull(overviewCountry.selected()?.id)
        val candidates = countryByUser.entries.filter { it.value !in taken }.groupBy({ it.value }, { it.key })
        if (offices.isEmpty() || candidates.isEmpty()) return
        try {
            val countries = wareraClient.getAllCountries().associateBy { it.id }
            candidates.forEach { (countryId, userIds) ->
                val country = countries[countryId] ?: return@forEach
                val government =
                    try {
                        wareraClient.getGovernment(countryId)
                    } catch (e: WareraApiException) {
                        log.warn("Government of country {} could not be loaded, no embassy thread for now", countryId, e)
                        return@forEach
                    }
                val office = offices.firstOrNull { office -> userIds.any { office.isHeldBy(government, it) } } ?: return@forEach
                val name = EmbassyThreadName.of(setting.threadNameTemplate, country.name)
                try {
                    create(
                        country,
                        name,
                        channelId,
                        AUTOMATIC,
                        "Botschafts-Thread $name automatisch angefordert: ${office.label} von ${country.name} ist verknüpft",
                    )
                } catch (e: ResponseStatusException) {
                    // An admin was quicker.
                    log.info("Embassy thread for country {} not requested: {}", countryId, e.reason)
                }
            }
        } catch (e: WareraApiException) {
            log.warn("Countries could not be loaded, no embassy threads for now", e)
        }
    }

    /** @throws ResponseStatusException 409 if the country has a thread or one is being created */
    private fun create(
        country: WareraCountry,
        name: String,
        channelId: String,
        actor: AuditActor,
        description: String,
    ) {
        try {
            transactionTemplate.executeWithoutResult {
                if (threadRepository.existsByCountryId(country.id)) throw conflict()
                creationRepository.findByCountryId(country.id)?.let { previous ->
                    if (previous.pending) throw conflict()
                    creationRepository.delete(previous)
                    creationRepository.flush()
                }
                val creation =
                    creationRepository.saveAndFlush(EmbassyThreadCreation(country.id, country.name, name, channelId, clock.instant()))
                eventPublisher.publish(
                    EmbassyThreadCreationEvent(creation.id!!, channelId, name, country.name, clock.millis()),
                )
                auditLog.record(actor, AuditAction.EMBASSY_THREAD_CREATION_REQUESTED, description)
            }
        } catch (e: DataIntegrityViolationException) {
            // A second click that came in at the same time.
            throw conflict()
        }
    }

    /** Removes a request that failed, once the admin has seen why. @throws ResponseStatusException 404, 409 while pending */
    fun dismiss(creationId: Long) =
        transactionTemplate.executeWithoutResult {
            val creation = creationRepository.findByIdOrNull(creationId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            if (creation.pending) throw conflict()
            creationRepository.delete(creation)
        }

    /**
     * Assigns the thread the bot created, or keeps why it failed. A report that comes again changes nothing. Returns
     * whether a thread was assigned, which then needs a sync.
     *
     * @throws ResponseStatusException 410 for a thread that comes after the request failed or went away, which the bot
     * deletes then; 400 for a report with neither thread nor failure
     */
    fun applyReport(
        creationId: Long,
        report: EmbassyThreadCreationReport,
    ): Boolean {
        val created = report.thread
        val assigned =
            transactionTemplate.execute {
                val creation = creationRepository.findByIdOrNull(creationId)?.takeIf { it.pending }
                if (creation == null) {
                    // The same report again, or a failure that came after the timeout.
                    if (created == null || threadRepository.existsById(created.id)) return@execute false
                    throw ResponseStatusException(HttpStatus.GONE, "Creation $creationId is no longer pending")
                }
                if (created == null) {
                    fail(creation, failureReason(report.failure ?: throw badRequest("Neither thread nor failure")))
                    return@execute false
                }
                // The bot reports its threads again soon; until then the new one shows with what is known.
                if (!discordThreadRepository.existsById(created.id)) {
                    val channel = guildChannels.all().firstOrNull { it.id == created.parentChannelId }
                    discordThreadRepository.save(
                        DiscordThread(
                            created.id,
                            created.name,
                            created.parentChannelId,
                            created.parentChannelName,
                            archived = false,
                            manageable = true,
                            everyoneCanView = channel?.everyoneCanView ?: true,
                            viewerRoleIds = channel?.viewerRoleIds.orEmpty(),
                        ),
                    )
                }
                creationRepository.delete(creation)
                threadRepository.save(
                    EmbassyThread(
                        created.id,
                        created.name,
                        created.parentChannelId,
                        creation.countryId,
                        creation.countryName,
                        clock.instant(),
                    ),
                )
                true
            }
        if (assigned == true) log.info("Embassy thread {} created for creation {}", created?.id, creationId)
        return assigned == true
    }

    /** Gives up on requests the bot has not answered within [TIMEOUT]. */
    @Scheduled(cron = "\${bits.embassy.creation-cron}")
    fun expire() {
        try {
            transactionTemplate.executeWithoutResult {
                creationRepository
                    .findByFailureIsNullAndRequestedAtBefore(clock.instant().minus(TIMEOUT))
                    .forEach { fail(it, "Der Bot hat nicht innerhalb einer Minute geantwortet.") }
            }
        } catch (e: Exception) {
            log.error("Could not expire embassy thread creations, trying again shortly", e)
        }
    }

    private fun fail(
        creation: EmbassyThreadCreation,
        reason: String,
    ) {
        log.warn("Embassy thread {} for {} not created: {}", creation.threadName, creation.countryName, reason)
        creation.failure = reason
        systemLog.recordProblem(SystemProblem.EmbassyThreadCreationFailed(creation.threadName, creation.countryName, reason))
    }

    private fun failureReason(failure: ThreadCreationFailure) =
        when (failure) {
            ThreadCreationFailure.CHANNEL_MISSING -> "Den Botschafts-Kanal gibt es nicht mehr, oder der Bot sieht ihn nicht."
            ThreadCreationFailure.NO_PERMISSION ->
                "Dem Bot fehlen im Botschafts-Kanal die Rechte „Private Threads erstellen“, „Threads verwalten“ oder " +
                    "„Nachrichten in Threads senden“."
            ThreadCreationFailure.FAILED -> "Discord hat das Anlegen abgelehnt."
        }

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private fun conflict() = ResponseStatusException(HttpStatus.CONFLICT, "Country has a thread or one is being created")

    companion object {
        /** Stands for BITS in the audit log when it asks for a thread on its own. */
        val AUTOMATIC = AuditActor("bits", "BITS (automatisch)", null)

        val TIMEOUT: Duration = Duration.ofMinutes(1)

        const val MAX_THREAD_NAME_LENGTH = EmbassyThreadName.MAX_LENGTH
    }
}
