import type { AuditAction } from '@/lib/api'

export const ACTIONS: Record<AuditAction, string> = {
  ROLE_RULE_CREATED: 'Regel angelegt',
  ROLE_RULE_DELETED: 'Regel gelöscht',
  ROLE_RULE_UPDATED: 'Regel bearbeitet',
  OVERVIEW_COUNTRY_SET: 'Land der Startseite geändert',
  NICKNAME_SETTING_CHANGED: 'Nicknames umgeschaltet',
  VOTE_STARTED: 'Abstimmung gestartet',
  VOTE_ENDED: 'Abstimmung beendet',
  VOTE_CANCELLED: 'Abstimmung abgebrochen',
  VOTE_MANAGER_ROLES_CHANGED: 'Rollen für Abstimmungen geändert',
  VOTE_RESULT_BACKGROUND_CHANGED: 'Hintergrund der Auswertung geändert',
  VOTE_PARTY_COLORS_CHANGED: 'Parteifarben geändert',
  EMBASSY_THREAD_ASSIGNED: 'Botschafts-Thread zugeordnet',
  EMBASSY_THREAD_UNASSIGNED: 'Botschafts-Thread aufgehoben',
  EMBASSY_MEMBER_ADDED: 'Mitglied zur Botschaft hinzugefügt',
  EMBASSY_MEMBER_REMOVED: 'Mitglied aus Botschaft entfernt',
  EMBASSY_SETTINGS_CHANGED: 'Botschafts-Einstellungen geändert',
  EMBASSY_THREAD_CREATION_REQUESTED: 'Botschafts-Thread angefordert',
  FEATURE_TOGGLED: 'Funktion umgeschaltet',
  BOT_PRESENCE_CHANGED: 'Bot-Status geändert',
  DSK_CREATED: 'DSK angelegt',
  DSK_UPDATED: 'DSK geändert',
  DSK_DELETED: 'DSK gelöscht',
  DSK_MILITARY_UNIT_ADDED: 'MU einer DSK zugeordnet',
  DSK_MILITARY_UNIT_REMOVED: 'MU aus DSK entfernt',
  STATUS_ROLES_CHANGED: 'Statusrollen geändert',
  EMBASSY_THREADS_RENAME_REQUESTED: 'Botschafts-Threads umbenannt',
  REMOVAL_LIMIT_TOGGLED: 'Schutz vor Massenentzug umgeschaltet',
  MEMBER_SYNC_REQUESTED: 'Mitglied abgeglichen',
  MEMBER_UNLINKED: 'Verknüpfung eines Mitglieds gelöst',
}

const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}

export const ERROR_MESSAGE = 'Das Audit-Log konnte nicht geladen werden. Bitte versuche es später erneut.'
