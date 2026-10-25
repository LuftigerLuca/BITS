import { ApiError } from '@/lib/api'

const NUMBER = new Intl.NumberFormat('de-DE')

export function formatCount(count: number): string {
  return NUMBER.format(count)
}

export const ERROR_MESSAGE = 'Die Kennzahlen konnten nicht geladen werden. Bitte versuche es später erneut.'

export function countryErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 400) return 'Dieses Land kennt WarEra nicht.'
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
