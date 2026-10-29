package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.RoleSyncEvent

/**
 * A consumer group of the bot and the event types it handles. Each lane reads the whole stream on its own and
 * acknowledges the events of the other lanes without handling them, so an event that takes long, like a role sync
 * that changes the roles of every member, holds up only its own lane. See ADR 0009.
 *
 * [MAIN] comes first and is the group the bot has always used; a lane added later starts where [MAIN] is, see
 * [RedisStreamEventConsumer].
 */
enum class EventLane(
    val group: String,
) {
    /** Everything but role syncs, including entries that are unreadable or of an unknown type. */
    MAIN(EventStream.BOT_GROUP) {
        override fun handles(type: String?) = type != ROLE_SYNC_TYPE
    },

    /** Role syncs, one after the other, so that newer ones win over older ones. */
    ROLE_SYNC(EventStream.BOT_ROLE_SYNC_GROUP) {
        override fun handles(type: String?) = type == ROLE_SYNC_TYPE
    },
    ;

    /** Whether this lane handles events with the `type` [type], `null` if the entry has none. */
    abstract fun handles(type: String?): Boolean
}

private val ROLE_SYNC_TYPE = RoleSyncEvent.serializer().descriptor.serialName
