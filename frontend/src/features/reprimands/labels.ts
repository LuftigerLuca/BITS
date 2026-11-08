import { ApiError } from '@/lib/api'

export const LOAD_ERROR = 'Die Rügen konnten nicht geladen werden. Bitte versuche es später erneut.'

export const SETTINGS_LOAD_ERROR = 'Die Einstellungen konnten nicht geladen werden. Bitte versuche es später erneut.'

export function settingsSaveErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) return 'Eine der Rollen gibt es nicht mehr.'
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

export function withdrawErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) return 'Diese Rüge gibt es nicht mehr.'
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

/** Discord's limit for a timeout. */
export const MAX_TIMEOUT_MINUTES = 28 * 24 * 60

export type TimeoutUnit = 'MINUTES' | 'HOURS' | 'DAYS'

export const UNITS: Record<TimeoutUnit, { label: string; minutes: number }> = {
  MINUTES: { label: 'Minuten', minutes: 1 },
  HOURS: { label: 'Stunden', minutes: 60 },
  DAYS: { label: 'Tage', minutes: 24 * 60 },
}

/** [minutes] in the largest unit that fits exactly, as the form shows it. */
export function splitTimeout(minutes: number): { amount: number; unit: TimeoutUnit } {
  if (minutes % UNITS.DAYS.minutes === 0) return { amount: minutes / UNITS.DAYS.minutes, unit: 'DAYS' }
  if (minutes % UNITS.HOURS.minutes === 0) return { amount: minutes / UNITS.HOURS.minutes, unit: 'HOURS' }
  return { amount: minutes, unit: 'MINUTES' }
}

const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}
