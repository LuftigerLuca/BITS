import { ApiError } from '@/lib/api'

export const ERROR_MESSAGE = 'Die Botschaften konnten nicht geladen werden. Bitte versuche es später erneut.'

/** Discord adds only members who may see the channel of a private thread. */
export const CANNOT_VIEW = {
  member:
    'Das Mitglied darf den Kanal des Threads in Discord nicht sehen, auch nicht mit der Botschafter-Rolle. Erlaube der Botschafter-Rolle dort „Kanal ansehen“.',
  roles:
    'Die Botschafter-Rolle oder eine Gastgeber-Rolle darf den Kanal eines Botschafts-Threads in Discord nicht sehen. Erlaube ihr dort „Kanal ansehen“, sonst kann BITS niemanden hinzufügen.',
}

/** [cannotView] says what a 422 means where the error came from. */
export function errorMessage(error: unknown, cannotView = CANNOT_VIEW.roles): string {
  if (error instanceof ApiError) {
    if (error.status === 422) return cannotView
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 409) return 'Der Thread oder das Land ist schon zugeordnet, oder das Mitglied ist schon drin.'
    if (error.status === 404) return 'Den Thread oder das Mitglied gibt es nicht mehr. Bitte lade die Seite neu.'
    if (error.status === 400) {
      return 'Die Angaben passen nicht, z. B. weil der Thread fehlt oder das Land unter „Verwaltung“ eingestellt ist.'
    }
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
