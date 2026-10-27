package eu.luftiger.bits.bot

import eu.luftiger.bits.bot.accounts.JdaDirectMessages
import eu.luftiger.bits.bot.accounts.RelinkNotifier
import eu.luftiger.bits.bot.backend.KtorBackendClient
import eu.luftiger.bits.bot.channels.ChannelReporter
import eu.luftiger.bits.bot.commands.NicknameCommand
import eu.luftiger.bits.bot.commands.PingCommand
import eu.luftiger.bits.bot.commands.ProfileCommand
import eu.luftiger.bits.bot.commands.RolesCommand
import eu.luftiger.bits.bot.commands.UnverifyCommand
import eu.luftiger.bits.bot.commands.VerifyCommand
import eu.luftiger.bits.bot.embassies.EmbassySyncHandler
import eu.luftiger.bits.bot.embassies.JdaEmbassyGuild
import eu.luftiger.bits.bot.embassies.ThreadReporter
import eu.luftiger.bits.bot.events.EventHandler
import eu.luftiger.bits.bot.events.LoggingEventHandler
import eu.luftiger.bits.bot.events.RedisStreamEventConsumer
import eu.luftiger.bits.bot.features.FeatureGate
import eu.luftiger.bits.bot.features.Features
import eu.luftiger.bits.bot.members.MemberReporter
import eu.luftiger.bits.bot.presence.PresenceUpdater
import eu.luftiger.bits.bot.roles.RoleReporter
import eu.luftiger.bits.bot.roles.RoleSyncHandler
import eu.luftiger.bits.bot.systemlog.SystemLogReporter
import eu.luftiger.bits.bot.votes.VoteButtons
import eu.luftiger.bits.bot.votes.VoteCommand
import eu.luftiger.bits.bot.votes.VoteEvents
import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.BotPresenceChangedEvent
import eu.luftiger.bits.shared.EmbassySyncEvent
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.VerificationCheckedEvent
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.shared.VoteStartedEvent
import io.lettuce.core.RedisClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("eu.luftiger.bits.bot.Main")

/** Time the bot takes to stop, below the 10 s Docker waits before it kills the container. */
private val SHUTDOWN_GRACE: Duration = Duration.ofSeconds(6)
private val CLOSE_TIMEOUT: Duration = Duration.ofSeconds(2)

fun main() {
    val config = BotConfig.fromEnv()
    val job = SupervisorJob()
    val scope = CoroutineScope(job + Dispatchers.IO)
    val backend = KtorBackendClient(config.backendUrl, config.serviceToken)
    val redis = RedisClient.create(config.redisUrl)
    val roleReporter = RoleReporter(config.guildId, backend, scope)
    val memberReporter = MemberReporter(config.guildId, backend, scope)
    val channelReporter = ChannelReporter(config.guildId, backend, scope)
    val threadReporter = ThreadReporter(config.guildId, backend, scope)
    val verifyCommand = VerifyCommand(backend, scope)
    val features = Features(backend, scope)
    features.refreshPeriodically()

    // The role sync and the member list need all members, which takes the privileged GUILD_MEMBERS intent.
    val jda =
        JDABuilder
            .createLight(config.discordToken)
            .enableIntents(GatewayIntent.GUILD_MEMBERS)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .addEventListeners(
                PingCommand(backend, scope),
                // Commands of a feature an admin switched off answer with a hint. Votes that are open run to their end,
                // so only starting one is gated; ending, cancelling and the buttons stay. Unlinking always works, since
                // members must be able to take back their data.
                FeatureGate(
                    features,
                    { it.verification },
                    VerifyCommand.NAME,
                    buttonPrefix = VerifyCommand.CHECK_BUTTON_PREFIX,
                    delegate = verifyCommand,
                ),
                UnverifyCommand(backend, scope),
                FeatureGate(
                    features,
                    { it.nicknames },
                    NicknameCommand.NAME,
                    buttonPrefix = NicknameCommand.OPT_IN_BUTTON,
                    delegate = NicknameCommand(backend, scope),
                ),
                FeatureGate(features, { it.profile }, ProfileCommand.NAME, delegate = ProfileCommand(backend, scope)),
                FeatureGate(features, { it.roleSync }, RolesCommand.NAME, delegate = RolesCommand(backend, scope)),
                FeatureGate(features, { it.votes }, VoteCommand.NAME, VoteCommand.START, delegate = VoteCommand(backend, scope)),
                VoteButtons(backend, scope),
                roleReporter,
                memberReporter,
                channelReporter,
                threadReporter,
            ).build()
            .awaitReady()

    val guild = jda.getGuildById(config.guildId) ?: error("Bot is not a member of guild ${config.guildId}")
    guild
        .updateCommands()
        .addCommands(
            PingCommand.data,
            VerifyCommand.data,
            UnverifyCommand.data,
            NicknameCommand.data,
            ProfileCommand.data,
            RolesCommand.data,
            VoteCommand.data,
        ).queue()
    roleReporter.reportPeriodically(guild)
    memberReporter.reportPeriodically(guild)
    channelReporter.reportPeriodically(guild)
    threadReporter.reportPeriodically(guild)
    val presence = PresenceUpdater(jda, backend, scope)
    presence.loadOnStart()

    val logging = LoggingEventHandler()
    val systemLog = SystemLogReporter(backend, scope, backend)
    val roleSync = RoleSyncHandler(jda, config.guildId, config.removalLimit, systemLog::reportRoleSync)
    val relinkNotifier = RelinkNotifier(JdaDirectMessages(jda))
    val votes = VoteEvents(jda, config.guildId, backend)
    val embassies = EmbassySyncHandler(JdaEmbassyGuild(jda, config.guildId), systemLog::reportEmbassySync)
    val events =
        EventHandler { event ->
            logging.handle(event)
            when (event) {
                is RoleSyncEvent -> roleSync.handle(event)
                is VerificationCheckedEvent -> verifyCommand.onChecked(event)
                is AccountLinkedEvent -> relinkNotifier.handle(event)
                is VoteStartedEvent -> votes.onStarted(event)
                is VoteClosedEvent -> votes.onClosed(event)
                is VoteEvaluatedEvent -> votes.onEvaluated(event)
                is EmbassySyncEvent -> embassies.handle(event)
                is BotPresenceChangedEvent -> presence.handle(event)
                else -> Unit
            }
        }
    val consumer = RedisStreamEventConsumer(redis, events, onDeadLetter = systemLog::reportUndeliveredEvent)
    // The consumer only ends when it is stopped. Anything else would leave a bot that answers commands but never
    // handles events again, so the process ends and Docker starts it anew.
    scope.launch(
        CoroutineExceptionHandler { _, e ->
            log.error("Event consumer died, exiting", e)
            // exitProcess blocks until the shutdown hook is done, which waits for this very coroutine.
            thread(name = "exit") { exitProcess(1) }
        },
    ) { consumer.run() }

    Runtime.getRuntime().addShutdownHook(
        Thread { shutdown(jda, consumer, job, backend, redis) },
    )
}

/**
 * Takes no new commands or events, gives running work [SHUTDOWN_GRACE] to finish and only then closes the clients it
 * needs. An event cut off by the grace period stays pending and is handled again after the restart.
 */
private fun shutdown(
    jda: JDA,
    consumer: RedisStreamEventConsumer,
    job: Job,
    backend: KtorBackendClient,
    redis: RedisClient,
) {
    log.info("Shutting down")
    jda.registeredListeners.forEach { jda.removeEventListener(it) }
    consumer.stop()
    runBlocking {
        val done = withTimeoutOrNull(SHUTDOWN_GRACE.toMillis()) { job.children.toList().joinAll() }
        if (done == null) log.warn("Work still running after {}, cancelling it", SHUTDOWN_GRACE)
    }
    // No join: a blocking Redis call only returns once the client below is shut down.
    job.cancel()
    jda.shutdown()
    if (!jda.awaitShutdown(CLOSE_TIMEOUT)) jda.shutdownNow()
    backend.close()
    redis.shutdown(0, CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
}
