package eu.luftiger.bits.bot

import eu.luftiger.bits.bot.backend.KtorBackendClient
import eu.luftiger.bits.bot.commands.PingCommand
import eu.luftiger.bits.bot.commands.VerifyCommand
import eu.luftiger.bits.bot.events.EventHandler
import eu.luftiger.bits.bot.events.LoggingEventHandler
import eu.luftiger.bits.bot.events.RedisStreamEventConsumer
import eu.luftiger.bits.bot.roles.RoleReporter
import eu.luftiger.bits.bot.roles.RoleSyncHandler
import eu.luftiger.bits.shared.RoleSyncEvent
import io.lettuce.core.RedisClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy

fun main() {
    val config = BotConfig.fromEnv()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val backend = KtorBackendClient(config.backendUrl, config.serviceToken)
    val redis = RedisClient.create(config.redisUrl)
    val roleReporter = RoleReporter(config.guildId, backend, scope)

    // The role sync needs all members, which takes the privileged GUILD_MEMBERS intent.
    val jda =
        JDABuilder
            .createLight(config.discordToken)
            .enableIntents(GatewayIntent.GUILD_MEMBERS)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setChunkingFilter(ChunkingFilter.ALL)
            .addEventListeners(PingCommand(backend, scope), VerifyCommand(backend, scope), roleReporter)
            .build()
            .awaitReady()

    val guild = jda.getGuildById(config.guildId) ?: error("Bot is not a member of guild ${config.guildId}")
    guild.updateCommands().addCommands(PingCommand.data, VerifyCommand.data).queue()
    roleReporter.report(guild)

    val logging = LoggingEventHandler()
    val roleSync = RoleSyncHandler(jda, config.guildId)
    val events =
        EventHandler { event ->
            logging.handle(event)
            if (event is RoleSyncEvent) roleSync.handle(event)
        }
    scope.launch { RedisStreamEventConsumer(redis, events).run() }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            scope.cancel()
            jda.shutdown()
            backend.close()
            redis.shutdown()
        },
    )
}
