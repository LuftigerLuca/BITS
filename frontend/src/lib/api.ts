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

export type AuditAction = 'ROLE_RULE_CREATED' | 'ROLE_RULE_DELETED'

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

export const DISCORD_LOGIN_URL = '/auth/oauth2/authorization/discord'
