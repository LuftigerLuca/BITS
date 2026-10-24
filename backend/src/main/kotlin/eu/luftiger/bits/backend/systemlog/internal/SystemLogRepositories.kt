package eu.luftiger.bits.backend.systemlog.internal

import eu.luftiger.bits.backend.systemlog.domain.SystemLogEntry
import eu.luftiger.bits.backend.systemlog.domain.SystemLogItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface SystemLogEntryRepository : JpaRepository<SystemLogEntry, Long> {
    /**
     * Newest first, [limit] entries with an ID below [before]. [kinds] are the kinds to show. With [member] (a Discord
     * ID or part of a name) or [roleId], an entry shows up if it is about them itself or has an item that matches both.
     */
    @Query(
        """
        SELECT e.* FROM system_log_entry e
        WHERE (CAST(:before AS BIGINT) IS NULL OR e.id < CAST(:before AS BIGINT))
          AND e.kind IN (:kinds)
          AND (
            (CAST(:member AS TEXT) IS NULL AND CAST(:roleId AS TEXT) IS NULL)
            OR (
              (CAST(:member AS TEXT) IS NULL
                OR e.discord_user_id = :member OR e.previous_discord_user_id = :member
                OR position(lower(:member) IN lower(coalesce(e.discord_name, ''))) > 0
                OR position(lower(:member) IN lower(coalesce(e.previous_discord_name, ''))) > 0)
              AND (CAST(:roleId AS TEXT) IS NULL OR e.role_id = :roleId)
            )
            OR EXISTS (
              SELECT 1 FROM system_log_item i
              WHERE i.entry_id = e.id
                AND (CAST(:member AS TEXT) IS NULL
                  OR i.discord_user_id = :member OR position(lower(:member) IN lower(coalesce(i.discord_name, ''))) > 0)
                AND (CAST(:roleId AS TEXT) IS NULL OR i.role_id = :roleId)
            )
          )
        ORDER BY e.id DESC
        LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findPage(
        kinds: Collection<String>,
        member: String?,
        roleId: String?,
        before: Long?,
        limit: Int,
    ): List<SystemLogEntry>

    /** Every role that appears in the log, with the name it had last. */
    @Query(
        """
        SELECT DISTINCT ON (r.role_id) r.role_id AS id, r.role_name AS name FROM (
          SELECT e.role_id, e.role_name, e.id AS entry_id FROM system_log_entry e WHERE e.role_id IS NOT NULL
          UNION ALL
          SELECT i.role_id, i.role_name, i.entry_id FROM system_log_item i WHERE i.role_id IS NOT NULL
        ) r
        ORDER BY r.role_id, (r.role_name IS NULL), r.entry_id DESC
        """,
        nativeQuery = true,
    )
    fun findRoles(): List<NameRow>

    /** The name each of [discordUserIds] had last in the log; members never named in it are missing. */
    @Query(
        """
        SELECT DISTINCT ON (n.discord_user_id) n.discord_user_id AS id, n.discord_name AS name FROM (
          SELECT e.discord_user_id, e.discord_name, e.id AS entry_id FROM system_log_entry e
          WHERE e.discord_user_id IN (:discordUserIds) AND e.discord_name IS NOT NULL
          UNION ALL
          SELECT e.previous_discord_user_id, e.previous_discord_name, e.id FROM system_log_entry e
          WHERE e.previous_discord_user_id IN (:discordUserIds) AND e.previous_discord_name IS NOT NULL
          UNION ALL
          SELECT i.discord_user_id, i.discord_name, i.entry_id FROM system_log_item i
          WHERE i.discord_user_id IN (:discordUserIds) AND i.discord_name IS NOT NULL
        ) n
        ORDER BY n.discord_user_id, n.entry_id DESC
        """,
        nativeQuery = true,
    )
    fun findLatestNames(discordUserIds: Collection<String>): List<NameRow>

    @Modifying
    @Query("DELETE FROM system_log_entry WHERE occurred_at < :before", nativeQuery = true)
    fun deleteOccurredBefore(before: Instant): Int

    interface NameRow {
        val id: String
        val name: String?
    }
}

interface SystemLogItemRepository : JpaRepository<SystemLogItem, Long> {
    /** The items of [entryId] that match [member] and [roleId], each if given, like in [SystemLogEntryRepository.findPage]. */
    @Query(
        """
        SELECT i.* FROM system_log_item i
        WHERE i.entry_id = :entryId
          AND (CAST(:member AS TEXT) IS NULL
            OR i.discord_user_id = :member OR position(lower(:member) IN lower(coalesce(i.discord_name, ''))) > 0)
          AND (CAST(:roleId AS TEXT) IS NULL OR i.role_id = :roleId)
        ORDER BY i.id
        """,
        nativeQuery = true,
    )
    fun findByEntry(
        entryId: Long,
        member: String?,
        roleId: String?,
    ): List<SystemLogItem>

    /** Which of [entryIds] have items, i.e. can be expanded in the dashboard. */
    @Query("SELECT DISTINCT i.entryId FROM SystemLogItem i WHERE i.entryId IN :entryIds")
    fun findEntryIdsWithItems(entryIds: Collection<Long>): List<Long>
}
