import type { ConditionType, CreateCondition, GovernmentOffice, MuRole, RuleCondition } from './api'
import { OFFICES } from './labels'

/** A condition as entered so far; `toCondition` tells whether it is complete. */
export interface ConditionDraft {
  /** Stays the same while the condition is edited, since any condition can be removed. */
  key: number
  /** Whether it starts a new block, i.e. is combined with the condition before by OR; ignored for the first one. */
  or: boolean
  type: ConditionType
  countryId: string | null
  /** Holding any of them is enough; none ticked at first, so no office is picked by accident. */
  offices: GovernmentOffice[]
  /** Optional for `MU_LEADERSHIP`: without, any military unit counts. */
  muId: string | null
  /** Name of the military unit `muId` of a saved condition, to show it before anything is searched. */
  muName: string | null
  muRole: MuRole
  threshold: string
  /**
   * Belongs to `countryId`, so it is cleared when the country changes. Optional for `PARTY_LEADER`: without, any party
   * of the country counts.
   */
  partyId: string | null
}

export function newDraft(key: number, or = false): ConditionDraft {
  return {
    key,
    or,
    type: 'CITIZENSHIP',
    countryId: null,
    offices: [],
    muId: null,
    muName: null,
    muRole: 'COMMANDER',
    threshold: '1',
    partyId: null,
  }
}

/** The drafts of the blocks of a saved rule, to edit it; keys from 0. */
export function draftsOf(blocks: { conditions: RuleCondition[] }[]): ConditionDraft[] {
  return blocks
    .flatMap((block, blockIndex) =>
      block.conditions.map((condition, index) => ({
        ...newDraft(0, blockIndex > 0 && index === 0),
        type: condition.type,
        countryId: condition.countryId,
        offices: condition.offices,
        muId: condition.muId,
        muName: condition.muId ? condition.targetName : null,
        muRole: condition.muRole ?? 'COMMANDER',
        threshold: String(condition.threshold ?? 1),
        partyId: condition.partyId,
      })),
    )
    .map((draft, key) => ({ ...draft, key }))
}

/** The drafts split into blocks at every OR; AND binds tighter than OR. */
export function blocksOf<T>(drafts: ConditionDraft[], toItem: (draft: ConditionDraft) => T): T[][] {
  const blocks: T[][] = []
  drafts.forEach((draft, index) => {
    if (index === 0 || draft.or) blocks.push([])
    blocks[blocks.length - 1].push(toItem(draft))
  })
  return blocks
}

export function needsCountry(type: ConditionType) {
  return type === 'CITIZENSHIP' || type === 'GOVERNMENT_OFFICE' || type === 'PARTY' || type === 'PARTY_LEADER'
}

/** `null` while a field the type needs is missing. */
export function toCondition(draft: ConditionDraft): CreateCondition | null {
  const { type, countryId, offices, muId, muRole, partyId } = draft
  switch (type) {
    case 'CITIZENSHIP':
      return countryId ? { type, countryId } : null
    case 'PARTY':
      return countryId && partyId ? { type, countryId, partyId } : null
    case 'GOVERNMENT_OFFICE':
      // By rank, like the backend, so the same offices in another order count as the same condition.
      return countryId && offices.length > 0 ? { type, countryId, offices: byRank(offices) } : null
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

const RANKS = Object.keys(OFFICES) as GovernmentOffice[]

function byRank(offices: GovernmentOffice[]) {
  return RANKS.filter((office) => offices.includes(office))
}
