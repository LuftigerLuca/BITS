import { ApiError, type AccountRoleOutcome } from '@/lib/api'

const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}

export const OUTCOMES: Record<AccountRoleOutcome, { label: string; variant: 'default' | 'secondary' | 'outline' }> = {
  MET: { label: 'Erfüllt', variant: 'default' },
  NOT_MET: { label: 'Nicht erfüllt', variant: 'secondary' },
  UNDECIDED: { label: 'Offen', variant: 'outline' },
}

export const ACCOUNT_ERROR = 'Dein Account konnte nicht geladen werden. Bitte versuche es später erneut.'

export const UNLINK_ERROR = 'Die Verknüpfung konnte nicht gelöst werden. Bitte versuche es später erneut.'

export function rolesErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 503) {
    return 'WarEra ist gerade nicht erreichbar, deine Rollen können nicht geprüft werden. Bitte versuche es später erneut.'
  }
  return 'Deine Rollen konnten nicht geladen werden. Bitte versuche es später erneut.'
}
