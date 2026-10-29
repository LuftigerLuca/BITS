import { ApiError } from '@/lib/api'

/** Stands for the English name of the country in the thread name template. */
export const PLACEHOLDER = '{land}'

/** Discord's limit for thread names, and the template's. */
export const MAX_THREAD_NAME_LENGTH = 100

/** Why [template] cannot be saved, in German; `null` if it can. Checked by the backend the same way. */
export function templateError(template: string): string | null {
  const trimmed = template.trim()
  if (trimmed.length === 0) return 'Der Thread-Name darf nicht leer sein.'
  if (trimmed.length > MAX_THREAD_NAME_LENGTH) return `Der Thread-Name darf höchstens ${MAX_THREAD_NAME_LENGTH} Zeichen haben.`
  const count = trimmed.split(PLACEHOLDER).length - 1
  if (count !== 1) return `Der Thread-Name muss ${PLACEHOLDER} genau einmal enthalten.`
  return null
}

/** The name of the thread of [countryName] after [template], cut to Discord's limit like the backend does. */
export function threadName(template: string, countryName: string): string {
  return template.replaceAll(PLACEHOLDER, countryName).slice(0, MAX_THREAD_NAME_LENGTH)
}

export const ERROR_MESSAGE = 'Die Botschaften konnten nicht geladen werden. Bitte versuche es später erneut.'

/** Discord adds only members who may see the channel of a private thread. */
export const CANNOT_VIEW = {
  member:
    'Das Mitglied darf den Kanal des Threads in Discord nicht sehen, auch nicht mit der Botschafter-Rolle. Erlaube der Botschafter-Rolle dort „Kanal ansehen“.',
  roles:
    'Die Botschafter-Rolle oder eine Gastgeber-Rolle darf den Kanal eines Botschafts-Threads oder den Botschafts-Kanal in Discord nicht sehen. Erlaube ihr dort „Kanal ansehen“, sonst kann BITS niemanden hinzufügen.',
  /** The bot may not create threads in the chosen embassy channel. */
  channel:
    'Dem Bot fehlen im Botschafts-Kanal die Rechte „Private Threads erstellen“, „Threads verwalten“ oder „Nachrichten in Threads senden“.',
}

/** [cannotView] says what a 422 means where the error came from. */
export function errorMessage(error: unknown, cannotView = CANNOT_VIEW.roles): string {
  if (error instanceof ApiError) {
    if (error.status === 422) return cannotView
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 409) {
      return 'Der Thread oder das Land ist schon zugeordnet, für das Land wird gerade ein Thread angelegt, oder das Mitglied ist schon drin.'
    }
    if (error.status === 404) return 'Den Thread oder das Mitglied gibt es nicht mehr. Bitte lade die Seite neu.'
    if (error.status === 400) {
      return 'Die Angaben passen nicht, z. B. weil der Thread oder der Botschafts-Kanal fehlt oder das Land unter „Verwaltung“ eingestellt ist.'
    }
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
