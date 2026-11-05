import { ApiError } from '@/lib/api'
import type { ApiTokenStatus } from './api'

export const LOAD_ERROR = 'Die API-Tokens konnten nicht geladen werden. Bitte versuche es später erneut.'

/** Where the documentation of the API is, open to everybody. */
export const DOCS_URL = '/external/docs'

/** The longest a token may be valid, as the backend allows. */
export const MAX_DAYS = 365

/** Preselected for a new token. */
export const DEFAULT_DAYS = 90

export const STATUS: Record<ApiTokenStatus, { label: string; variant: 'success' | 'warning' | 'destructive' }> = {
  ACTIVE: { label: 'Aktiv', variant: 'success' },
  EXPIRING: { label: 'Läuft bald ab', variant: 'warning' },
  EXPIRED: { label: 'Abgelaufen', variant: 'destructive' },
}

export function createErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) {
    return 'Prüfe Name, Ablaufdatum (höchstens ein Jahr) und Scopes.'
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

export function revokeErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) return 'Das Token gibt es nicht mehr.'
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

const DATE = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium' })
const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

/** The last day a token is valid, the day before [expiresAt]. */
export function formatLastDay(expiresAt: number): string {
  return DATE.format(expiresAt - 1)
}

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}

/** `YYYY-MM-DD` of the local day [days] days from [from]. */
export function isoDay(days: number, from: Date = new Date()): string {
  const day = new Date(from.getFullYear(), from.getMonth(), from.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}`
}
