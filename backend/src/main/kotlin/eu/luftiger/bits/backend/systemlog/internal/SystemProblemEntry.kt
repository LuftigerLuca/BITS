package eu.luftiger.bits.backend.systemlog.internal

import eu.luftiger.bits.backend.systemlog.api.EmbassyThreadCause
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.systemlog.domain.EntryKind
import eu.luftiger.bits.backend.systemlog.domain.SystemLogEntry
import java.time.Instant

private const val MAX_ERROR_LENGTH = 500

/** The entry that tells admins about [problem], with the text they read. */
fun entryOf(
    problem: SystemProblem,
    now: Instant,
): SystemLogEntry =
    when (problem) {
        is SystemProblem.WareraUsersUnavailable ->
            SystemLogEntry(
                now,
                EntryKind.WARERA_USERS_UNAVAILABLE,
                message =
                    "Die WarEra-Daten von ${members(problem.discordUserIds.distinct().size)} konnten nicht geladen werden. Ihre Rollen " +
                        "bleiben, wie sie sind.",
            )
        is SystemProblem.GovernmentUnavailable ->
            SystemLogEntry(
                now,
                EntryKind.GOVERNMENT_UNAVAILABLE,
                message =
                    "Die Regierung von ${problem.countryName ?: problem.countryId} konnte nicht geladen werden. " +
                        "Regeln für Regierungsämter dieses Landes wurden übersprungen.",
            )
        is SystemProblem.PartiesUnavailable ->
            SystemLogEntry(
                now,
                EntryKind.PARTIES_UNAVAILABLE,
                message =
                    "Die Parteien eines Landes konnten nicht geladen werden. Regeln für " +
                        "${problem.partyNames.joinToString(", ")} wurden übersprungen.",
            )
        is SystemProblem.MilitaryUnitUnavailable ->
            SystemLogEntry(
                now,
                EntryKind.MILITARY_UNIT_UNAVAILABLE,
                message =
                    "Die MU ${problem.muName ?: problem.muId} konnte nicht geladen werden. " +
                        "Regeln für die Führung dieser MU wurden übersprungen.",
            )
        is SystemProblem.EmbassyGovernmentUnavailable ->
            SystemLogEntry(
                now,
                EntryKind.GOVERNMENT_UNAVAILABLE,
                message =
                    "Die Regierung von ${problem.countryName} konnte nicht geladen werden. Aus ihrem Botschafts-Thread " +
                        "wurde niemand wegen des Amts entfernt.",
            )
        is SystemProblem.EmbassyThreadUnavailable ->
            SystemLogEntry(
                now,
                EntryKind.EMBASSY_THREAD_UNAVAILABLE,
                message =
                    "Der Botschafts-Thread ${problem.threadName} (${problem.countryName}) wurde nicht abgeglichen: " +
                        when (problem.cause) {
                            EmbassyThreadCause.MISSING -> "Er wurde gelöscht, oder der Bot sieht ihn nicht."
                            EmbassyThreadCause.NO_PERMISSION ->
                                "Dem Bot fehlen im Kanal die Rechte „Threads verwalten“ oder „Nachrichten in Threads senden“."
                            EmbassyThreadCause.FAILED -> "Discord hat die Anfrage abgelehnt."
                        },
            )
        is SystemProblem.EmbassyThreadCreationFailed ->
            SystemLogEntry(
                now,
                EntryKind.EMBASSY_THREAD_CREATION_FAILED,
                message =
                    "Der Botschafts-Thread ${problem.threadName} (${problem.countryName}) wurde nicht angelegt: " +
                        problem.reason,
            )
        is SystemProblem.EmbassyThreadRenameFailed ->
            SystemLogEntry(
                now,
                EntryKind.EMBASSY_THREAD_RENAME_FAILED,
                message =
                    "Der Botschafts-Thread ${problem.threadName} (${problem.countryName}) wurde nicht umbenannt: " +
                        when (problem.cause) {
                            EmbassyThreadCause.MISSING -> "Er wurde gelöscht, oder der Bot sieht ihn nicht."
                            EmbassyThreadCause.NO_PERMISSION -> "Dem Bot fehlt im Kanal das Recht „Threads verwalten“."
                            EmbassyThreadCause.FAILED -> "Discord hat die Anfrage abgelehnt."
                        },
            )
        is SystemProblem.RoleNotAssignable ->
            SystemLogEntry(
                now,
                EntryKind.ROLE_NOT_ASSIGNABLE,
                roleId = problem.roleId,
                roleName = problem.roleName,
                message =
                    if (problem.roleName == null) {
                        "Die Rolle wurde in Discord gelöscht. Ihre Regeln laufen nicht mehr."
                    } else {
                        "Der Bot kann die Rolle nicht vergeben, z. B. weil sie über seiner Rolle steht. Ihre Regeln " +
                            "laufen nicht mehr."
                    },
            )
        is SystemProblem.SyncAborted ->
            SystemLogEntry(
                now,
                EntryKind.SYNC_ABORTED,
                syncTrigger = problem.trigger,
                message = "Der Sync ist abgebrochen: ${problem.error.take(MAX_ERROR_LENGTH)}",
            )
        is SystemProblem.VoteEvaluationDelayed ->
            SystemLogEntry(
                now,
                EntryKind.VOTE_EVALUATION_DELAYED,
                message =
                    "Die Auswertung der Abstimmung „${problem.question}“ ist fehlgeschlagen und wird alle paar Minuten " +
                        "wiederholt: ${problem.error.take(MAX_ERROR_LENGTH)}",
            )
        is SystemProblem.OutboxStuck ->
            SystemLogEntry(
                now,
                EntryKind.OUTBOX_STUCK,
                message =
                    "${problem.count} Events für den Bot warten seit über 10 Minuten auf die Zustellung. Redis ist " +
                        "vermutlich nicht erreichbar; sie werden zugestellt, sobald es wieder geht.",
            )
        is SystemProblem.MemberReportRefused ->
            SystemLogEntry(
                now,
                EntryKind.MEMBER_REPORT_REFUSED,
                message =
                    "Laut der Mitgliederliste des Bots hätten ${problem.departedCount} von ${problem.linkedCount} " +
                        "verknüpften Mitgliedern den Server verlassen. Das sieht nach einer fehlerhaften Liste aus, " +
                        "daher wurde keine Verknüpfung gelöscht.",
            )
    }

private fun members(count: Int) = if (count == 1) "einem Mitglied" else "$count Mitgliedern"
