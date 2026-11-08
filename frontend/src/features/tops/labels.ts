import { ApiError } from '@/lib/api'

export const LOAD_ERROR = 'Die TOP-Foren konnten nicht geladen werden. Bitte versuche es später erneut.'

export function saveErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 422) return 'Dem Bot fehlen in einem der Foren Rechte.'
  if (error instanceof ApiError && error.status === 400) return 'Ein Forum oder Tag gibt es nicht mehr.'
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
