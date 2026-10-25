import type { ItemOutcome, LogCategory, NamedRef, SyncRunStatus, SyncTrigger, SystemLogEntry } from '@/lib/api'

export const CATEGORIES: Record<LogCategory, string> = {
  SYNC: 'Sync-Läufe',
  ACCOUNT: 'Account-Ereignisse',
  ERROR: 'Fehler',
}

/** Short name of the category for the table. */
export const CATEGORY_BADGES: Record<LogCategory, { label: string; variant: 'secondary' | 'outline' | 'destructive' }> = {
  SYNC: { label: 'Sync', variant: 'secondary' },
  ACCOUNT: { label: 'Account', variant: 'outline' },
  ERROR: { label: 'Fehler', variant: 'destructive' },
}

export const TRIGGERS: Record<SyncTrigger, string> = {
  HOURLY: 'stündlich',
  RULE_CHANGE: 'nach Regeländerung',
  ACCOUNT_CHANGE: 'nach Verknüpfung',
}

export const STATUS: Record<SyncRunStatus, { label: string; variant: 'secondary' | 'outline' | 'destructive' }> = {
  PENDING: { label: 'Ausstehend', variant: 'outline' },
  APPLIED: { label: 'Angewendet', variant: 'secondary' },
  STALE: { label: 'Übersprungen', variant: 'outline' },
  NO_RULES: { label: 'Keine Regeln', variant: 'outline' },
  NO_RESPONSE: { label: 'Keine Rückmeldung vom Bot', variant: 'destructive' },
}

export const OUTCOMES: Record<ItemOutcome, string> = {
  GRANTED: 'Vergeben',
  REMOVED: 'Entzogen',
  SKIPPED: 'Übersprungen',
  FAILED: 'Fehlgeschlagen',
}

export function name(ref: NamedRef): string {
  return ref.name ?? ref.id
}

/** One line on what happened, in German. */
export function describe(entry: SystemLogEntry): string {
  const sync = entry.sync
  if (sync) {
    const title = sync.trigger ? `Sync ${TRIGGERS[sync.trigger]}` : 'Sync'
    if (sync.memberCount === null) {
      return sync.linkedCount === null ? title : `${title}: ${sync.linkedCount} verknüpfte Accounts`
    }
    const counts = [
      `${sync.memberCount} Mitglieder`,
      `${sync.granted} vergeben`,
      `${sync.removed} entzogen`,
      `${sync.skipped} übersprungen`,
      `${sync.failed} fehlgeschlagen`,
    ]
    return `${title}: ${counts.join(' · ')}`
  }
  const member = entry.member ? name(entry.member) : 'Unbekannt'
  if (entry.type === 'ACCOUNT_LINKED') return `${member} hat den WarEra-Account ${entry.wareraUsername} verknüpft.`
  if (entry.type === 'ACCOUNT_RELINKED') {
    const previous = entry.previousMember ? name(entry.previousMember) : 'einem anderen Account'
    return `${member} hat den WarEra-Account ${entry.wareraUsername} von ${previous} übernommen.`
  }
  if (entry.type === 'ACCOUNT_REMOVED') {
    return `${member} hat den Server verlassen, die Verknüpfung mit ${entry.wareraUsername} wurde entfernt.`
  }
  const role = entry.role ? `Rolle ${name(entry.role)}: ` : ''
  return `${role}${entry.message ?? ''}`
}

const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}

export const ERROR_MESSAGE = 'Das System-Log konnte nicht geladen werden. Bitte versuche es später erneut.'
