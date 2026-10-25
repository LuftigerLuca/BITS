// Talks to the backend on the same origin (nginx in production, the Vite proxy in development), so the
// HttpOnly session cookie is sent along without any CORS setup.

export type Role = 'USER' | 'ADMIN'

export interface Me {
  name: string
  avatarUrl: string | null
  role: Role
  wareraUsername: string | null
}

export interface AuthOptions {
  adminLogin: boolean
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed with status ${status}`)
    this.status = status
  }
}

let onUnauthorized: () => void = () => {}

/** Called whenever the backend rejects the session, e.g. because it expired. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(path, { credentials: 'same-origin', ...init })
  if (response.status === 401 && path.startsWith('/web/')) onUnauthorized()
  if (!response.ok) throw new ApiError(response.status)
  return response
}

export async function getMe(): Promise<Me> {
  return (await request('/web/me')).json()
}

export async function getAuthOptions(): Promise<AuthOptions> {
  return (await request('/auth/options')).json()
}

export async function adminLogin(username: string, password: string): Promise<void> {
  await request('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export async function logout(): Promise<void> {
  await request('/auth/logout', { method: 'POST' })
}

export interface DiscordRole {
  id: string
  name: string
  color: number | null
  /** Whether a rule exists for the role already, i.e. BITS manages it. */
  managed: boolean
}

export type ConditionType =
  | 'CITIZENSHIP'
  | 'GOVERNMENT_OFFICE'
  | 'MILITARY_UNIT'
  | 'LEVEL'
  | 'MILITARY_RANK'

export type GovernmentOffice =
  | 'PRESIDENT'
  | 'VICE_PRESIDENT'
  | 'MINISTER_OF_ECONOMY'
  | 'MINISTER_OF_DEFENSE'
  | 'MINISTER_OF_FOREIGN_AFFAIRS'
  | 'CONGRESS'

export type RuleStatus = 'ACTIVE' | 'ORPHANED' | 'NOT_ASSIGNABLE'

export interface RoleRule {
  id: number
  roleId: string
  /** `null` if the role was deleted in Discord. */
  roleName: string | null
  roleColor: number | null
  label: string
  status: RuleStatus
}

/** Which of the optional fields are needed depends on `type`. */
export interface CreateRoleRule {
  roleId: string
  type: ConditionType
  countryId?: string
  office?: GovernmentOffice
  muId?: string
  threshold?: number
}

export interface Option {
  id: string
  name: string
}

const ROLE_SYNC = '/web/admin/role-sync'

export async function getRoles(): Promise<DiscordRole[]> {
  return (await request(`${ROLE_SYNC}/roles`)).json()
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

export async function deleteRoleRule(id: number): Promise<void> {
  await request(`${ROLE_SYNC}/rules/${id}`, { method: 'DELETE' })
}

export async function getCountries(): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/countries`)).json()
}

export async function searchMilitaryUnits(query: string): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/military-units?${new URLSearchParams({ query })}`)).json()
}

const AUDIT_LOG = '/web/admin/audit-log'

export type AuditAction = 'ROLE_RULE_CREATED' | 'ROLE_RULE_DELETED' | 'OVERVIEW_COUNTRY_SET'

/** `ADMIN_ACCOUNT` is the admin account from the environment, which has no Discord avatar. */
export type ActorKind = 'DISCORD' | 'ADMIN_ACCOUNT'

export interface AuditActor {
  /** What to filter by; `discord:<id>` or `admin:<username>`. */
  subject: string
  name: string
  avatarUrl: string | null
  kind: ActorKind
}

export interface AuditEntry {
  id: number
  /** Epoch milliseconds. */
  occurredAt: number
  actor: AuditActor
  action: AuditAction
  description: string
}

export interface AuditLogPage {
  entries: AuditEntry[]
  /** Pass as `before` for the next page; `null` on the last one. */
  nextBefore: number | null
}

export interface AuditLogFilter {
  actor: string | null
  action: AuditAction | null
}

/** Newest first, a page at a time; the backend applies the filter. */
export async function getAuditLog(filter: AuditLogFilter, before: number | null): Promise<AuditLogPage> {
  const params = new URLSearchParams()
  if (filter.actor) params.set('actor', filter.actor)
  if (filter.action) params.set('action', filter.action)
  if (before !== null) params.set('before', String(before))
  return (await request(`${AUDIT_LOG}?${params}`)).json()
}

/** Everybody who appears in the audit log, former admins too. */
export async function getAuditActors(): Promise<AuditActor[]> {
  return (await request(`${AUDIT_LOG}/actors`)).json()
}

const SYSTEM_LOG = '/web/admin/system-log'

export type LogCategory = 'SYNC' | 'ACCOUNT' | 'ERROR'

export type LogEntryType =
  | 'SYNC_RUN'
  | 'ACCOUNT_LINKED'
  | 'ACCOUNT_RELINKED'
  | 'ACCOUNT_REMOVED'
  | 'WARERA_USERS_UNAVAILABLE'
  | 'GOVERNMENT_UNAVAILABLE'
  | 'ROLE_NOT_ASSIGNABLE'
  | 'SYNC_ABORTED'
  | 'EVENT_UNDELIVERED'
  | 'OUTBOX_STUCK'
  | 'MEMBER_REPORT_REFUSED'

export type SyncTrigger = 'HOURLY' | 'RULE_CHANGE' | 'ACCOUNT_CHANGE'

/** `NO_RESPONSE`: still `PENDING` 15 minutes after the sync. */
export type SyncRunStatus = 'PENDING' | 'APPLIED' | 'STALE' | 'NO_RULES' | 'NO_RESPONSE'

export type ItemOutcome = 'GRANTED' | 'REMOVED' | 'SKIPPED' | 'FAILED'

/** A member or role by its Discord ID; `name` is `null` if BITS never saw it. */
export interface NamedRef {
  id: string
  name: string | null
}

export interface SyncRun {
  trigger: SyncTrigger | null
  status: SyncRunStatus
  /** Linked accounts when the sync started. */
  linkedCount: number | null
  /** The counts are `null` until the bot reports back. */
  memberCount: number | null
  staleMemberCount: number | null
  granted: number | null
  removed: number | null
  skipped: number | null
  failed: number | null
}

export interface SystemLogEntry {
  id: number
  /** Epoch milliseconds. */
  occurredAt: number
  type: LogEntryType
  category: LogCategory
  sync: SyncRun | null
  member: NamedRef | null
  previousMember: NamedRef | null
  wareraUsername: string | null
  role: NamedRef | null
  /** German description of an error. */
  message: string | null
  /** Whether the entry has role changes or members to expand. */
  hasItems: boolean
}

export interface SystemLogItem {
  id: number
  member: NamedRef
  role: NamedRef | null
  outcome: ItemOutcome
  reason: string | null
}

export interface SystemLogPage {
  entries: SystemLogEntry[]
  /** Pass as `before` for the next page; `null` on the last one. */
  nextBefore: number | null
}

export interface SystemLogFilter {
  category: LogCategory | null
  /** A Discord ID or part of a name. */
  member: string | null
  roleId: string | null
}

function systemLogParams(filter: Partial<SystemLogFilter>, before?: number | null) {
  const params = new URLSearchParams()
  if (filter.category) params.set('category', filter.category)
  if (filter.member) params.set('member', filter.member)
  if (filter.roleId) params.set('roleId', filter.roleId)
  if (before != null) params.set('before', String(before))
  return params
}

/** Newest first, a page at a time; the backend applies the filter. */
export async function getSystemLog(filter: SystemLogFilter, before: number | null): Promise<SystemLogPage> {
  return (await request(`${SYSTEM_LOG}?${systemLogParams(filter, before)}`)).json()
}

/** The role changes of a sync run or the members of an error, filtered by member and role like the entries. */
export async function getSystemLogItems(
  entryId: number,
  filter: Pick<SystemLogFilter, 'member' | 'roleId'>,
): Promise<SystemLogItem[]> {
  return (await request(`${SYSTEM_LOG}/${entryId}/items?${systemLogParams(filter)}`)).json()
}

/** Every role that appears in the system log. */
export async function getSystemLogRoles(): Promise<NamedRef[]> {
  return (await request(`${SYSTEM_LOG}/roles`)).json()
}

const OVERVIEW = '/web/admin/overview'

export interface CountryOverview {
  id: string
  name: string
  /** Whether WarEra could be reached; if not, the counts are `null`. */
  available: boolean
  citizens: number | null
  activeCitizens: number | null
}

export interface Overview {
  /** Members of the Discord without bots; `null` while the bot has not reported them since the backend started. */
  members: number | null
  /** Of `members`, those with a linked WarEra account. */
  verifiedMembers: number | null
  /** `null` while no country is set. */
  country: CountryOverview | null
}

export async function getOverview(): Promise<Overview> {
  return (await request(OVERVIEW)).json()
}

export async function setOverviewCountry(countryId: string): Promise<Option> {
  const response = await request(`${OVERVIEW}/country`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ countryId }),
  })
  return response.json()
}

export const DISCORD_LOGIN_URL = '/auth/oauth2/authorization/discord'
