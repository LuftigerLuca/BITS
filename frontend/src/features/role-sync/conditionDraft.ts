import type { ConditionType, CreateCondition, GovernmentOffice } from '@/lib/api'

/** A condition as entered so far; `toCondition` tells whether it is complete. */
export interface ConditionDraft {
  /** Stays the same while the condition is edited, since any condition can be removed. */
  key: number
  type: ConditionType
  countryId: string | null
  office: GovernmentOffice
  muId: string | null
  threshold: string
}

export function newDraft(key: number): ConditionDraft {
  return {
    key,
    type: 'CITIZENSHIP',
    countryId: null,
    office: 'PRESIDENT',
    muId: null,
    threshold: '1',
  }
}

export function needsCountry(type: ConditionType) {
  return type === 'CITIZENSHIP' || type === 'GOVERNMENT_OFFICE'
}

/** `null` while a field the type needs is missing. */
export function toCondition(draft: ConditionDraft): CreateCondition | null {
  const { type, countryId, office, muId } = draft
  switch (type) {
    case 'CITIZENSHIP':
      return countryId ? { type, countryId } : null
    case 'GOVERNMENT_OFFICE':
      return countryId ? { type, countryId, office } : null
    case 'MILITARY_UNIT':
      return muId ? { type, muId } : null
    case 'LEVEL':
    case 'MILITARY_RANK': {
      const value = Number(draft.threshold)
      return Number.isInteger(value) && value >= 1 ? { type, threshold: value } : null
    }
  }
}
