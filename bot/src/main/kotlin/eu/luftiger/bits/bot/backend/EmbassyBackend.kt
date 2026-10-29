package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.DiscordThreadDto
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.EmbassyThreadRenameReport

/** The part of the backend's REST API for the embassies; [KtorBackendClient] implements it next to [BackendClient]. */
interface EmbassyBackend {
    /** Replaces the private threads of the guild known to the backend. */
    suspend fun reportThreads(threads: List<DiscordThreadDto>)

    /** Tells the backend what the bot did with the embassy sync [syncId]. */
    suspend fun reportEmbassySync(
        syncId: Long,
        report: EmbassySyncReport,
    )

    /** Tells the backend what became of the embassy thread creation [creationId]; 410 if it no longer wants the thread. */
    suspend fun reportThreadCreation(
        creationId: Long,
        report: EmbassyThreadCreationReport,
    )

    /** Tells the backend which embassy threads the bot could not rename. */
    suspend fun reportThreadRenames(report: EmbassyThreadRenameReport)
}
