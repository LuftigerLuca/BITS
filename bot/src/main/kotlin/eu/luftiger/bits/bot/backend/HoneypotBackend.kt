package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.HoneypotKickReport
import eu.luftiger.bits.shared.HoneypotPauseReport
import eu.luftiger.bits.shared.HoneypotProblemReport
import eu.luftiger.bits.shared.HoneypotState
import eu.luftiger.bits.shared.HoneypotWarningReport

/** The part of the backend's REST API for the honeypot; [KtorBackendClient] implements it. */
interface HoneypotBackend {
    suspend fun honeypot(): HoneypotState

    suspend fun reportHoneypotWarning(report: HoneypotWarningReport)

    suspend fun reportHoneypotKick(report: HoneypotKickReport)

    suspend fun reportHoneypotPause(report: HoneypotPauseReport)

    suspend fun reportHoneypotProblem(report: HoneypotProblemReport)
}
