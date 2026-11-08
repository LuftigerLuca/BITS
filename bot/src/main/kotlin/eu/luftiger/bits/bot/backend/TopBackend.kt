package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.TopActionReport
import eu.luftiger.bits.shared.TopActor
import eu.luftiger.bits.shared.TopPermissionResponse

/** The part of the backend's REST API for `/topclose` and `/topopen`; [KtorBackendClient] implements it. */
interface TopBackend {
    /** Whether [actor] may close or reopen a post of the forum [forumId], and which tag a closed one gets. */
    suspend fun topPermission(
        actor: TopActor,
        forumId: String,
    ): TopPermissionResponse

    /** For the audit log, once the post is closed or reopened. */
    suspend fun reportTopAction(report: TopActionReport)
}
