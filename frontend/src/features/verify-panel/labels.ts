import { ApiError } from '@/lib/api'

export const LOAD_ERROR = 'Der Verifizierungs-Kanal konnte nicht geladen werden. Bitte versuche es später erneut.'

export function saveErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 422) return 'Der Bot darf in diesem Kanal nicht schreiben.'
  if (error instanceof ApiError && error.status === 400) return 'Diesen Kanal gibt es nicht mehr.'
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
