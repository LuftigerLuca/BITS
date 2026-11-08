package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.VerifyPanelProblemReport
import eu.luftiger.bits.shared.VerifyPanelReport
import eu.luftiger.bits.shared.VerifyPanelState

/** The part of the backend's REST API for the message with the button to verify; [KtorBackendClient] implements it. */
interface VerifyPanelBackend {
    suspend fun verifyPanel(): VerifyPanelState

    suspend fun reportVerifyPanelMessage(report: VerifyPanelReport)

    suspend fun reportVerifyPanelProblem(report: VerifyPanelProblemReport)
}
