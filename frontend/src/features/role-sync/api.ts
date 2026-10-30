import { request, type Option } from '@/lib/api'

export interface DiscordRole {
  id: string
  name: string
  color: number | null
  /** Whether a rule exists for the role already, i.e. BITS manages it. */
  managed: boolean
  /** The DSK the role belongs to, `null` if none; such a role cannot get a rule. */
  dskName: string | null
  /** Whether it is the role for verified or unverified members; such a role cannot get a rule. */
  statusRole: boolean
}

export type ConditionType =
  | 'CITIZENSHIP'
  | 'GOVERNMENT_OFFICE'
  | 'MILITARY_UNIT'
  | 'LEVEL'
  | 'MILITARY_RANK'
  | 'COMPANIES'
  | 'PARTY'
  | 'MU_LEADERSHIP'
  | 'PARTY_LEADER'

export type GovernmentOffice =
  | 'PRESIDENT'
  | 'VICE_PRESIDENT'
  | 'MINISTER_OF_ECONOMY'
  | 'MINISTER_OF_DEFENSE'
  | 'MINISTER_OF_FOREIGN_AFFAIRS'
  | 'CONGRESS'

/** A rank in a military unit, lowest first; each includes the ranks above it. */
export type MuRole = 'COMMANDER' | 'MANAGER' | 'OWNER'

export type RuleStatus = 'ACTIVE' | 'ORPHANED' | 'NOT_ASSIGNABLE'

/** The rule of a role, the only one it has. */
export interface RoleRule {
  roleId: string
  /** `null` if the role was deleted in Discord. */
  roleName: string | null
  roleColor: number | null
  /** The blocks joined with „oder“. */
  label: string
  /** Of these, one is enough. */
  blocks: RuleBlock[]
  status: RuleStatus
}

/** All of the conditions must be met. */
export interface RuleBlock {
  /** The conditions joined with „und“. */
  label: string
  conditions: RuleCondition[]
}

/** A saved condition, with the fields to edit it. */
export interface RuleCondition {
  type: ConditionType
  countryId: string | null
  offices: GovernmentOffice[]
  muId: string | null
  threshold: number | null
  partyId: string | null
  muRole: MuRole | null
  /** Name of the country, military unit or party when the condition was saved. */
  targetName: string | null
  label: string
}

/**
 * One of the blocks must be met, and all conditions of a block; between one and `MAX_BLOCKS` blocks of between one and
 * `MAX_CONDITIONS` conditions.
 */
export interface CreateRoleRule {
  roleId: string
  blocks: CreateCondition[][]
}

/** The backend takes at most this many conditions per block. */
export const MAX_CONDITIONS = 10

/** The backend takes at most this many blocks per rule. */
export const MAX_BLOCKS = 10

/** Which of the optional fields are needed depends on `type`. */
export interface CreateCondition {
  type: ConditionType
  countryId?: string
  /** For `GOVERNMENT_OFFICE`, at least one; holding any of them is enough. */
  offices?: GovernmentOffice[]
  muId?: string
  threshold?: number
  partyId?: string
  muRole?: MuRole
}

export interface PreviewMember {
  discordUserId: string
  /** `null` for a member who joined after the bot's last member list. */
  name: string | null
}

/** Whom a rule change gives its role and from whom it takes it; nothing is saved. */
export interface RulePreview {
  roleId: string
  roleName: string | null
  /** `false` if the role has no rule after the change: BITS no longer manages it, and nobody gets or loses it. */
  managed: boolean
  /** `false` until the bot has reported the members with their roles since the backend started; there is no preview. */
  membersReported: boolean
  /** `false` until the WarEra data of all linked members was loaded since the backend started. */
  usersLoaded: boolean
  gains: PreviewMember[]
  losses: PreviewMember[]
  /** Members whose WarEra data is missing; the sync keeps their role or loads their data first. */
  unknown: PreviewMember[]
}

const ROLE_SYNC = '/web/admin/role-sync'

export async function getRoles(): Promise<DiscordRole[]> {
  return (await request(`${ROLE_SYNC}/roles`)).json()
}

/** A role for verified or unverified members; like a rule, only an active one is given. */
export interface StatusRole {
  roleId: string
  /** `null` if the role was deleted in Discord. */
  roleName: string | null
  roleColor: number | null
  status: RuleStatus
}

export interface StatusRoles {
  /** `null` if none is set. */
  verified: StatusRole | null
  unverified: StatusRole | null
}

export async function getStatusRoles(): Promise<StatusRoles> {
  return (await request(`${ROLE_SYNC}/status-roles`)).json()
}

/** `null` for none. 400 if both are the same or a new one cannot be given, 409 if it has a rule or belongs to a DSK. */
export async function setStatusRoles(roles: {
  verifiedRoleId: string | null
  unverifiedRoleId: string | null
}): Promise<void> {
  await request(`${ROLE_SYNC}/status-roles`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(roles),
  })
}

/** Whether the bot holds back a role that a sync of all members would take from many of its holders at once. */
export async function getRemovalLimit(): Promise<{ enabled: boolean }> {
  return (await request(`${ROLE_SYNC}/removal-limit`)).json()
}

/** Switching it off syncs all members right away, without the limit. */
export async function setRemovalLimit(enabled: boolean): Promise<void> {
  await request(`${ROLE_SYNC}/removal-limit`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export async function getRoleRules(): Promise<RoleRule[]> {
  return (await request(`${ROLE_SYNC}/rules`)).json()
}

export async function createRoleRule(rule: CreateRoleRule): Promise<RoleRule> {
  const response = await request(`${ROLE_SYNC}/rules`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(rule),
  })
  return response.json()
}

/** Replaces the blocks of the rule of the role as a whole. */
export async function updateRoleRule(rule: CreateRoleRule): Promise<RoleRule> {
  const response = await request(`${ROLE_SYNC}/rules/${rule.roleId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ blocks: rule.blocks }),
  })
  return response.json()
}

/** What `rule` would change as the rule of its role, in place of the one it has. */
export async function previewRoleRule(rule: CreateRoleRule): Promise<RulePreview> {
  const response = await request(`${ROLE_SYNC}/rules/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(rule),
  })
  return response.json()
}

export async function previewDeleteRoleRule(roleId: string): Promise<RulePreview> {
  return (await request(`${ROLE_SYNC}/rules/${roleId}/deletion-preview`)).json()
}

export async function deleteRoleRule(roleId: string): Promise<void> {
  await request(`${ROLE_SYNC}/rules/${roleId}`, { method: 'DELETE' })
}

export async function getParties(countryId: string): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/countries/${encodeURIComponent(countryId)}/parties`)).json()
}

export async function searchMilitaryUnits(query: string): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/military-units?${new URLSearchParams({ query })}`)).json()
}
