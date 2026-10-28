import type { ConditionType, CreateCondition, GovernmentOffice, MuRole } from '@/lib/api'

/** A condition as entered so far; `toCondition` tells whether it is complete. */
export interface ConditionDraft {
  /** Stays the same while the condition is edited, since any condition can be removed. */
  key: number
  type: ConditionType
  countryId: string | null
  office: GovernmentOffice
  /** Optional for `MU_LEADERSHIP`: without, any military unit counts. */
  muId: string | null
  muRole: MuRole
  threshold: string
  /**
   * Belongs to `countryId`, so it is cleared when the country changes. Optional for `PARTY_LEADER`: without, any party
   * of the country counts.
   */
  partyId: string | null
}

export function newDraft(key: number): ConditionDraft {
  return {
    key,
    type: 'CITIZENSHIP',
    countryId: null,
    office: 'PRESIDENT',
    muId: null,
    muRole: 'COMMANDER',
    threshold: '1',
    partyId: null,
  }
}

export function needsCountry(type: ConditionType) {
  return type === 'CITIZENSHIP' || type === 'GOVERNMENT_OFFICE' || type === 'PARTY' || type === 'PARTY_LEADER'
}

/** `null` while a field the type needs is missing. */
export function toCondition(draft: ConditionDraft): CreateCondition | null {
  const { type, countryId, office, muId, muRole, partyId } = draft
  switch (type) {
    case 'CITIZENSHIP':
      return countryId ? { type, countryId } : null
    case 'PARTY':
      return countryId && partyId ? { type, countryId, partyId } : null
    case 'GOVERNMENT_OFFICE':
      return countryId ? { type, countryId, office } : null
    case 'PARTY_LEADER':
      if (!countryId) return null
      return partyId ? { type, countryId, partyId } : { type, countryId }
    case 'MILITARY_UNIT':
      return muId ? { type, muId } : null
    case 'MU_LEADERSHIP':
      return muId ? { type, muId, muRole } : { type, muRole }
    case 'LEVEL':
    case 'MILITARY_RANK':
    case 'COMPANIES': {
      const value = Number(draft.threshold)
      return Number.isInteger(value) && value >= 1 ? { type, threshold: value } : null
    }
  }
}
