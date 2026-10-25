import { ApiError } from '@/lib/api'

export const ERROR_MESSAGE = 'Die Botschaften konnten nicht geladen werden. Bitte versuche es später erneut.'

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 409) return 'Der Thread oder das Land ist schon zugeordnet, oder das Mitglied ist schon drin.'
    if (error.status === 404) return 'Den Thread oder das Mitglied gibt es nicht mehr. Bitte lade die Seite neu.'
    if (error.status === 400) {
      return 'Die Angaben passen nicht, z. B. weil der Thread fehlt oder das Land unter „Verwaltung“ eingestellt ist.'
    }
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
