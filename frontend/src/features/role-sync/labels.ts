import { ApiError, type ConditionType, type GovernmentOffice, type MuRole, type RuleStatus } from '@/lib/api'

export const CONDITION_TYPES: Record<ConditionType, string> = {
  CITIZENSHIP: 'Staatsbürgerschaft',
  GOVERNMENT_OFFICE: 'Regierungsamt',
  MILITARY_UNIT: 'Military Unit',
  LEVEL: 'Level',
  MILITARY_RANK: 'Militärrang',
  COMPANIES: 'Firmenbesitz',
  PARTY: 'Partei',
  MU_LEADERSHIP: 'MU-Führung',
  PARTY_LEADER: 'Parteivorsitz',
}

/** Each includes the ranks above it. */
export const MU_ROLES: Record<MuRole, string> = {
  COMMANDER: 'Kommandeur',
  MANAGER: 'Manager',
  OWNER: 'Besitzer',
}

export const OFFICES: Record<GovernmentOffice, string> = {
  PRESIDENT: 'Präsident',
  VICE_PRESIDENT: 'Vizepräsident',
  MINISTER_OF_ECONOMY: 'Wirtschaftsminister',
  MINISTER_OF_DEFENSE: 'Verteidigungsminister',
  MINISTER_OF_FOREIGN_AFFAIRS: 'Außenminister',
  CONGRESS: 'Kongressmitglied',
}

export const STATUS: Record<RuleStatus, { label: string; variant: 'secondary' | 'destructive' | 'outline' }> = {
  ACTIVE: { label: 'Aktiv', variant: 'secondary' },
  ORPHANED: { label: 'Verwaist', variant: 'destructive' },
  NOT_ASSIGNABLE: { label: 'Nicht vergebbar', variant: 'outline' },
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 400) {
      return 'Die Regel ist unvollständig, hat eine Bedingung oder einen Block doppelt oder die Rolle kann nicht vergeben werden.'
    }
    if (error.status === 409) {
      return 'Für diese Rolle gibt es schon eine Regel, oder sie gehört zu einer DSK oder ist eine Statusrolle.'
    }
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}
