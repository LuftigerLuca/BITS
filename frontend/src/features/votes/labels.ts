import { ApiError, type VoteStatus } from '@/lib/api'

export const STATUS: Record<VoteStatus, { label: string; variant: 'secondary' | 'outline' | 'destructive' }> = {
  OPEN: { label: 'Läuft', variant: 'secondary' },
  COUNTING: { label: 'Wird ausgewertet', variant: 'outline' },
  EVALUATED: { label: 'Ausgewertet', variant: 'secondary' },
  CANCELLED: { label: 'Abgebrochen', variant: 'outline' },
}

/** What the end field offers first, like the bot's default. */
export const DEFAULT_DURATION_MILLIS = 48 * 60 * 60 * 1000

const TIME = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' })

export function formatTime(epochMillis: number): string {
  return TIME.format(epochMillis)
}

/** The value a `datetime-local` input takes, in the browser's time zone. */
export function toLocalInput(epochMillis: number): string {
  const date = new Date(epochMillis)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

export function participation(voted: number | null, eligible: number | null): string {
  if (voted === null) return '–'
  if (eligible === null) return `${voted} haben abgestimmt`
  return `${voted} von ${eligible} haben abgestimmt`
}

/** [conflict] says what a 409 means where the error came from. */
export function errorMessage(error: unknown, conflict = 'Die Abstimmung läuft nicht mehr.'): string {
  if (error instanceof ApiError) {
    if (error.status === 400) return 'Die Abstimmung ist unvollständig, oder ihr Ende liegt in der Vergangenheit.'
    if (error.status === 403) return 'Abstimmungen verwalten dürfen nur Admins und die dafür freigegebenen Rollen.'
    if (error.status === 409) return conflict
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

export const BACKGROUND_TOO_LARGE = 'Das Bild ist größer als 5 MB.'

export function backgroundErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 413) return BACKGROUND_TOO_LARGE
    if (error.status === 400 || error.status === 415) return 'Die Datei ist kein PNG- oder JPEG-Bild, das BITS lesen kann.'
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

export function partyColorsErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 409) return 'Unter „Startseite“ ist kein Land mehr eingestellt.'
    if (error.status === 400) return 'Eine der Parteien gehört nicht mehr zu diesem Land. Bitte lade die Seite neu.'
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
