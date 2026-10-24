import type { AuditAction } from '@/lib/api'

export const ACTIONS: Record<AuditAction, string> = {
  ROLE_RULE_CREATED: 'Regel angelegt',
  ROLE_RULE_DELETED: 'Regel gelöscht',
}

const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}

export const ERROR_MESSAGE = 'Das Audit-Log konnte nicht geladen werden. Bitte versuche es später erneut.'
