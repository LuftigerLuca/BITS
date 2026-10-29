import { ApiError, type LogEntryType, type MemberLogEvent } from '@/lib/api'
import { OUTCOMES, TRIGGERS } from '@/features/system-log/labels'

/** Short names of the entries that are no sync, for the events of one member. */
const KINDS: Record<LogEntryType, string> = {
  SYNC_RUN: 'Sync',
  EMBASSY_SYNC_RUN: 'Botschafts-Sync',
  ACCOUNT_LINKED: 'Verknüpft',
  ACCOUNT_RELINKED: 'Verknüpfung übernommen',
  ACCOUNT_REMOVED: 'Server verlassen',
  WARERA_USERS_UNAVAILABLE: 'WarEra-Daten nicht abrufbar',
  GOVERNMENT_UNAVAILABLE: 'Regierung nicht abrufbar',
  PARTIES_UNAVAILABLE: 'Parteien nicht abrufbar',
  MILITARY_UNIT_UNAVAILABLE: 'MU nicht abrufbar',
  ROLE_NOT_ASSIGNABLE: 'Rolle nicht vergebbar',
  SYNC_ABORTED: 'Sync abgebrochen',
  EVENT_UNDELIVERED: 'Event nicht zugestellt',
  OUTBOX_STUCK: 'Events hängen',
  MEMBER_REPORT_REFUSED: 'Mitgliederliste abgelehnt',
  VOTE_EVALUATION_DELAYED: 'Auswertung verzögert',
  EMBASSY_THREAD_UNAVAILABLE: 'Botschafts-Thread nicht erreichbar',
  EMBASSY_THREAD_CREATION_FAILED: 'Botschafts-Thread nicht angelegt',
  EMBASSY_THREAD_RENAME_FAILED: 'Botschafts-Thread nicht umbenannt',
}

/** One line on what happened to the member, in German. */
export function describeEvent(event: MemberLogEvent): string {
  if (event.outcome !== null) {
    const kind = event.syncTrigger ? `${KINDS[event.kind]} ${TRIGGERS[event.syncTrigger]}` : KINDS[event.kind]
    const role = event.role ? `${event.role} ` : ''
    const reason = event.reason ? ` (${event.reason})` : ''
    return `${kind}: ${role}${OUTCOMES[event.outcome].toLowerCase()}${reason}`
  }
  const account = event.wareraUsername ?? 'unbekannt'
  switch (event.kind) {
    case 'ACCOUNT_LINKED':
      return `WarEra-Account ${account} verknüpft`
    case 'ACCOUNT_RELINKED':
      return event.previousAccount
        ? `WarEra-Account ${account} an einen anderen Discord-Account verloren`
        : `WarEra-Account ${account} von einem anderen Discord-Account übernommen`
    case 'ACCOUNT_REMOVED':
      return `Server verlassen, Verknüpfung mit ${account} entfernt`
    default:
      return KINDS[event.kind]
  }
}

export const ON_SERVER = (onServer: boolean | null) =>
  onServer === null ? 'unbekannt' : onServer ? 'ja' : 'nein, hat den Server verlassen'

export const SEARCH_ERROR = 'Die Mitglieder konnten nicht gesucht werden. Bitte versuche es später erneut.'

export const MEMBER_ERROR = 'Das Mitglied konnte nicht geladen werden. Bitte versuche es später erneut.'

export const UNLINK_ERROR = 'Die Verknüpfung konnte nicht gelöst werden. Bitte versuche es später erneut.'

export function syncErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) {
    return 'Rollen-Sync, Nicknames und Statusrollen sind alle ausgeschaltet, es gibt nichts abzugleichen.'
  }
  return 'Der Abgleich konnte nicht angestoßen werden. Bitte versuche es später erneut.'
}

export function memberErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return 'Dieses Mitglied ist weder auf dem Server noch verknüpft.'
  }
  return MEMBER_ERROR
}

export function rolesErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 503) {
    return 'WarEra ist gerade nicht erreichbar, die Rollen können nicht geprüft werden. Bitte versuche es später erneut.'
  }
  return 'Die Rollen konnten nicht geladen werden. Bitte versuche es später erneut.'
}
