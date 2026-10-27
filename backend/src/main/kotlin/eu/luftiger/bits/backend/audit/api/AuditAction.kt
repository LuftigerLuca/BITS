package eu.luftiger.bits.backend.audit.api

import kotlinx.serialization.Serializable
import org.springframework.modulith.NamedInterface

/**
 * What an admin, or a member allowed to manage votes, did. Stored by name and part of the dashboard API, so existing
 * names stay as they are; the dashboard spells them out in `frontend/src/lib/api.ts`.
 */
@NamedInterface("api")
@Serializable
enum class AuditAction {
    ROLE_RULE_CREATED,
    ROLE_RULE_DELETED,
    OVERVIEW_COUNTRY_SET,

    /** No longer recorded since members opt in themselves; kept for existing entries. */
    NICKNAME_SETTING_CHANGED,
    VOTE_STARTED,
    VOTE_ENDED,
    VOTE_CANCELLED,
    VOTE_MANAGER_ROLES_CHANGED,
    VOTE_RESULT_BACKGROUND_CHANGED,
    VOTE_PARTY_COLORS_CHANGED,
    EMBASSY_THREAD_ASSIGNED,
    EMBASSY_THREAD_UNASSIGNED,
    EMBASSY_MEMBER_ADDED,
    EMBASSY_MEMBER_REMOVED,
    EMBASSY_SETTINGS_CHANGED,
    BOT_PRESENCE_CHANGED,
}
