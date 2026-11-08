package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.ReprimandRequest
import eu.luftiger.bits.shared.ReprimandResponse
import eu.luftiger.bits.shared.ReprimandTimeoutProblemReport

/** The part of the backend's REST API for `/reprimand`; [KtorBackendClient] implements it. */
interface ReprimandBackend {
    /** Stores the reprimand if the actor may give it; the bot then sets the timeout. */
    suspend fun reprimand(request: ReprimandRequest): ReprimandResponse

    /** For the system log, when a timeout could not be set or lifted. */
    suspend fun reportReprimandTimeoutProblem(report: ReprimandTimeoutProblemReport)
}
