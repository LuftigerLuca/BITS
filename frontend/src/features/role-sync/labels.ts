import { ApiError, type ConditionType, type GovernmentOffice, type RuleStatus } from '@/lib/api'

export const CONDITION_TYPES: Record<ConditionType, string> = {
  CITIZENSHIP: 'Staatsbürgerschaft',
  GOVERNMENT_OFFICE: 'Regierungsamt',
  MILITARY_UNIT: 'Military Unit',
  LEVEL: 'Level',
  MILITARY_RANK: 'Militärrang',
}

export const OFFICES: Record<GovernmentOffice, string> = {
  PRESIDENT: 'Präsident',
  VICE_PRESIDENT: 'Vizepräsident',
  MINISTER_OF_ECONOMY: 'Wirtschaftsminister',
  MINISTER_OF_DEFENSE: 'Verteidigungsminister',
  MINISTER_OF_FOREIGN_AFFAIRS: 'Außenminister',
  CONGRESS: 'Kongress',
}

export const STATUS: Record<RuleStatus, { label: string; variant: 'secondary' | 'destructive' | 'outline' }> = {
  ACTIVE: { label: 'Aktiv', variant: 'secondary' },
  ORPHANED: { label: 'Verwaist', variant: 'destructive' },
  NOT_ASSIGNABLE: { label: 'Nicht vergebbar', variant: 'outline' },
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 400) return 'Die Regel ist unvollständig oder die Rolle kann nicht vergeben werden.'
    if (error.status === 409) return 'Diese Regel gibt es für die Rolle schon.'
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
