package eu.luftiger.bits.backend.audit.api

import kotlinx.serialization.Serializable
import org.springframework.modulith.NamedInterface

/**
 * What an admin did. Stored by name and part of the dashboard API, so existing names stay as they are; the dashboard
 * spells them out in `frontend/src/lib/api.ts`.
 */
@NamedInterface("api")
@Serializable
enum class AuditAction {
    ROLE_RULE_CREATED,
    ROLE_RULE_DELETED,
    OVERVIEW_COUNTRY_SET,
    NICKNAME_SETTING_CHANGED,
}
