package eu.luftiger.bits.bot

data class BotConfig(
    val discordToken: String,
    /** The one guild the bot serves; slash commands are registered there. */
    val guildId: String,
    val backendUrl: String,
    val serviceToken: String,
    val redisUrl: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): BotConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() } ?: error("Environment variable $name is not set")

            return BotConfig(
                discordToken = required("DISCORD_TOKEN"),
                guildId = required("DISCORD_GUILD_ID"),
                backendUrl = env["BACKEND_URL"] ?: "http://localhost:8080",
                serviceToken = required("BITS_SERVICE_TOKEN"),
                redisUrl = env["REDIS_URL"] ?: "redis://localhost:6379",
            )
        }
    }
}
