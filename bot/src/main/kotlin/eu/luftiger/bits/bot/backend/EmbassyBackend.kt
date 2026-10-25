package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.DiscordThreadDto
import eu.luftiger.bits.shared.EmbassySyncReport

/** The part of the backend's REST API for the embassies; [KtorBackendClient] implements it next to [BackendClient]. */
interface EmbassyBackend {
    /** Replaces the private threads of the guild known to the backend. */
    suspend fun reportThreads(threads: List<DiscordThreadDto>)

    /** Tells the backend what the bot did with the embassy sync [syncId]. */
    suspend fun reportEmbassySync(
        syncId: Long,
        report: EmbassySyncReport,
    )
}
