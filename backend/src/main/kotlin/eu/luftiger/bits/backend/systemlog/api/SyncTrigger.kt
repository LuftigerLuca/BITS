package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/** Why a sync ran. */
@NamedInterface("api")
enum class SyncTrigger {
    /** The hourly sync of all members with fresh WarEra data. */
    HOURLY,

    /** An admin created or deleted a rule. */
    RULE_CHANGE,

    /** A member linked or unlinked their WarEra account. */
    ACCOUNT_CHANGE,

    /**
     * An admin changed a setting the sync depends on, e.g. of the embassies. Before, also when an admin turned on
     * nicknames for everybody.
     */
    SETTING_CHANGE,
}
