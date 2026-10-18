package eu.luftiger.bits.bot

import eu.luftiger.bits.bot.backend.KtorBackendClient
import eu.luftiger.bits.bot.commands.PingCommand
import eu.luftiger.bits.bot.events.LoggingEventHandler
import eu.luftiger.bits.bot.events.RedisStreamEventConsumer
import io.lettuce.core.RedisClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDABuilder

fun main() {
    val config = BotConfig.fromEnv()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val backend = KtorBackendClient(config.backendUrl, config.serviceToken)
    val redis = RedisClient.create(config.redisUrl)

    val jda =
        JDABuilder
            .createLight(config.discordToken)
            .addEventListeners(PingCommand(backend, scope))
            .build()
            .awaitReady()

    val guild = jda.getGuildById(config.guildId) ?: error("Bot is not a member of guild ${config.guildId}")
    guild.updateCommands().addCommands(PingCommand.data).queue()

    scope.launch { RedisStreamEventConsumer(redis, LoggingEventHandler()).run() }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            scope.cancel()
            jda.shutdown()
            backend.close()
            redis.shutdown()
        },
    )
}
