import { ApiError, type BotActivityType, type BotStatus } from '@/lib/api'

export const STATUS: Record<BotStatus, string> = {
  ONLINE: 'Online',
  IDLE: 'Abwesend',
  DO_NOT_DISTURB: 'Bitte nicht stören',
  INVISIBLE: 'Unsichtbar',
}

/** As Discord shows the activity in front of its text; `CUSTOM` shows the text alone. */
export const ACTIVITY: Record<BotActivityType, string> = {
  PLAYING: 'Spielt …',
  WATCHING: 'Schaut …',
  LISTENING: 'Hört …',
  COMPETING: 'Tritt an in …',
  CUSTOM: 'Eigener Status-Text',
}

export const LOAD_ERROR = 'Der Bot-Status konnte nicht geladen werden. Bitte versuche es später erneut.'

export function saveErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) return 'Der Text der Aktivität ist leer oder zu lang.'
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
