import { ApiError } from '@/lib/api'

export const ERROR_MESSAGE = 'Die Militäreinheiten konnten nicht geladen werden. Bitte versuche es später erneut.'

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 409) {
      return 'Der Name, die Rolle oder die MU ist schon vergeben, oder die Rolle hat eine Regel unter „Rollen“.'
    }
    if (error.status === 404) return 'Die DSK oder die MU gibt es nicht mehr. Bitte lade die Seite neu.'
    if (error.status === 400) {
      return 'Die Angaben passen nicht, z. B. weil der Name fehlt, die Rolle nicht vergeben werden kann oder WarEra die MU nicht kennt.'
    }
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
