// Talks to the backend on the same origin (nginx in production, the Vite proxy in development), so the
// HttpOnly session cookie is sent along without any CORS setup.

export type Role = 'USER' | 'ADMIN'

export interface Me {
  name: string
  avatarUrl: string | null
  role: Role
  wareraUsername: string | null
  /** `false` for the admin account from the environment, which has no Discord account and no page "Mein Account". */
  discordUser: boolean
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

export interface Option {
  id: string
  name: string
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

export async function getCountries(): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/countries`)).json()
}

export async function getParties(countryId: string): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/countries/${encodeURIComponent(countryId)}/parties`)).json()
}

export async function searchMilitaryUnits(query: string): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/military-units?${new URLSearchParams({ query })}`)).json()
}

/** A role a DSK may get: only one without a rule, no status role and of no other DSK. */
export interface DskRoleOption {
  id: string
  name: string
  color: number | null
  hasRule: boolean
  statusRole: boolean
  dskId: number | null
}

export type DskRoleStatus = 'ACTIVE' | 'ORPHANED' | 'NOT_ASSIGNABLE'

/** A DSK: the linked members of its military units get its role. */
export interface Dsk {
  id: number
  name: string
  /** `null` without a role; the DSK then changes nothing. */
  roleId: string | null
  /** `null` without a role or if it was deleted in Discord. */
  roleName: string | null
  roleColor: number | null
  roleStatus: DskRoleStatus | null
  /** By name, as of the time they were assigned. */
  militaryUnits: Option[]
}

export interface MilitaryUnits {
  /** The roles the bot can give, highest first. */
  roles: DskRoleOption[]
  dsks: Dsk[]
}

export interface MilitaryUnitOption {
  id: string
  name: string
  /** The country the unit is based in, `null` if WarEra names none. */
  countryName: string | null
  /** The DSK the unit belongs to already, `null` if none. */
  dskName: string | null
}

export interface DskRequest {
  name: string
  roleId: string | null
}

const MILITARY_UNITS = '/web/admin/military-units'

export async function getMilitaryUnits(): Promise<MilitaryUnits> {
  return (await request(MILITARY_UNITS)).json()
}

export async function createDsk(dsk: DskRequest): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dsk),
  })
}

/** Renames the DSK and sets its role. */
export async function updateDsk(id: number, dsk: DskRequest): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dsk),
  })
}

export async function deleteDsk(id: number): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${id}`, { method: 'DELETE' })
}

/** Military units of all countries whose name matches [query]. */
export async function searchDskMilitaryUnits(query: string): Promise<MilitaryUnitOption[]> {
  return (await request(`${MILITARY_UNITS}/search?${new URLSearchParams({ query })}`)).json()
}

export async function addDskMilitaryUnit(dskId: number, muId: string): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${dskId}/military-units`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ muId }),
  })
}

export async function removeDskMilitaryUnit(dskId: number, muId: string): Promise<void> {
  await request(`${MILITARY_UNITS}/dsks/${dskId}/military-units/${encodeURIComponent(muId)}`, { method: 'DELETE' })
}

const AUDIT_LOG = '/web/admin/audit-log'

export type AuditAction =
  | 'ROLE_RULE_CREATED'
  | 'ROLE_RULE_DELETED'
  | 'ROLE_RULE_UPDATED'
  | 'OVERVIEW_COUNTRY_SET'
  | 'NICKNAME_SETTING_CHANGED'
  | 'VOTE_STARTED'
  | 'VOTE_ENDED'
  | 'VOTE_CANCELLED'
  | 'VOTE_MANAGER_ROLES_CHANGED'
  | 'VOTE_RESULT_BACKGROUND_CHANGED'
  | 'VOTE_PARTY_COLORS_CHANGED'
  | 'EMBASSY_THREAD_ASSIGNED'
  | 'EMBASSY_THREAD_UNASSIGNED'
  | 'EMBASSY_MEMBER_ADDED'
  | 'EMBASSY_MEMBER_REMOVED'
  | 'EMBASSY_SETTINGS_CHANGED'
  | 'EMBASSY_THREAD_CREATION_REQUESTED'
  | 'FEATURE_TOGGLED'
  | 'DSK_CREATED'
  | 'DSK_UPDATED'
  | 'DSK_DELETED'
  | 'DSK_MILITARY_UNIT_ADDED'
  | 'DSK_MILITARY_UNIT_REMOVED'
  | 'STATUS_ROLES_CHANGED'
  | 'EMBASSY_THREADS_RENAME_REQUESTED'
  | 'BOT_PRESENCE_CHANGED'

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
  | 'EMBASSY_SYNC_RUN'
  | 'ACCOUNT_LINKED'
  | 'ACCOUNT_RELINKED'
  | 'ACCOUNT_REMOVED'
  | 'WARERA_USERS_UNAVAILABLE'
  | 'GOVERNMENT_UNAVAILABLE'
  | 'PARTIES_UNAVAILABLE'
  | 'MILITARY_UNIT_UNAVAILABLE'
  | 'ROLE_NOT_ASSIGNABLE'
  | 'SYNC_ABORTED'
  | 'EVENT_UNDELIVERED'
  | 'OUTBOX_STUCK'
  | 'MEMBER_REPORT_REFUSED'
  | 'VOTE_EVALUATION_DELAYED'
  | 'EMBASSY_THREAD_UNAVAILABLE'
  | 'EMBASSY_THREAD_CREATION_FAILED'
  | 'EMBASSY_THREAD_RENAME_FAILED'

export type SyncTrigger = 'HOURLY' | 'RULE_CHANGE' | 'ACCOUNT_CHANGE' | 'SETTING_CHANGE'

/** `NO_RESPONSE`: still `PENDING` 15 minutes after the sync. */
export type SyncRunStatus = 'PENDING' | 'APPLIED' | 'STALE' | 'NO_RULES' | 'NO_RESPONSE'

export type ItemOutcome =
  | 'GRANTED'
  | 'REMOVED'
  | 'SKIPPED'
  | 'FAILED'
  | 'NICKNAME_SET'
  | 'NICKNAME_RESET'
  | 'THREAD_ADDED'
  | 'THREAD_REMOVED'

/** A member or role by its Discord ID; `name` is `null` if BITS never saw it. */
export interface NamedRef {
  id: string
  name: string | null
}

export interface SyncRun {
  trigger: SyncTrigger | null
  status: SyncRunStatus
  /** Linked accounts when the sync started; embassy threads for `EMBASSY_SYNC_RUN`. */
  linkedCount: number | null
  /** Synced members, threads for `EMBASSY_SYNC_RUN`; the counts are `null` until the bot reports back. */
  memberCount: number | null
  staleMemberCount: number | null
  granted: number | null
  removed: number | null
  skipped: number | null
  failed: number | null
  /** Nicknames set or cleared; skipped and failed ones are in `skipped` and `failed`. `null` from an older bot. */
  nicknames: number | null
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

const EMBASSIES = '/web/admin/embassies'

export interface EmbassySettings {
  /** Whose holders belong in the thread of their country. */
  offices: GovernmentOffice[]
  /** `null` for none: BITS then gives and takes no role. */
  ambassadorRoleId: string | null
  /** Their members belong in every thread, without getting the ambassador role. */
  hostRoleIds: string[]
  /** Where the bot creates embassy threads; `null` for none. */
  channelId: string | null
  /** The name of embassy threads, with `{land}` exactly once for the English name of the country. */
  threadNameTemplate: string
}

export interface EmbassyChannel {
  id: string
  name: string
  category: string | null
  /** Whether the bot may create and manage private threads there. */
  threadsCreatable: boolean
}

/** A thread the bot is creating, or failed to create. */
export interface EmbassyCreation {
  id: number
  countryId: string
  countryName: string
  threadName: string
  /** Why it failed, in German; `null` while the bot is creating it. */
  failure: string | null
}

export interface MemberOption {
  discordUserId: string
  /** `null` if the bot has not reported the member. */
  name: string | null
}

export interface EmbassyThread {
  threadId: string
  threadName: string
  /** `null` if the bot no longer reports the thread. */
  channelName: string | null
  countryId: string
  countryName: string
  /** Whether the bot no longer reports the thread, e.g. because it was deleted. */
  missing: boolean
  /** Whether the thread is archived and named other than after the template, so renaming skips it. */
  renameSkipped: boolean
  /** Who is in the thread and why, as of the last sync; empty until the bot reported one. */
  members: { discordUserId: string; name: string; reason: string }[]
  /** Added by an admin; they stay regardless of any office. */
  manualMembers: MemberOption[]
}

export interface Embassies {
  /** The country under "Verwaltung", which gets no thread. */
  ownCountryId: string | null
  settings: EmbassySettings
  /** Highest first. */
  roles: { id: string; name: string; color: number | null }[]
  /** Text channels in the order of Discord. */
  channels: EmbassyChannel[]
  /** By country name. */
  threads: EmbassyThread[]
  /** Private threads without a country yet. */
  availableThreads: {
    id: string
    name: string
    channelName: string
    archived: boolean
    manageable: boolean
  }[]
  /** Being created by the bot, or failed; by country name. */
  creations: EmbassyCreation[]
}

export async function getEmbassies(): Promise<Embassies> {
  return (await request(EMBASSIES)).json()
}

export async function setEmbassySettings(settings: EmbassySettings): Promise<void> {
  await request(`${EMBASSIES}/settings`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
}

/** 409 if the thread or the country has one already, 503 if WarEra cannot be reached. */
export async function assignEmbassyThread(threadId: string, countryId: string): Promise<void> {
  await request(`${EMBASSIES}/threads`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ threadId, countryId }),
  })
}

/** 400 without an embassy channel, 409 if the country has a thread or one is being created. */
export async function createEmbassyThread(countryId: string, threadName: string): Promise<void> {
  await request(`${EMBASSIES}/creations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ countryId, threadName }),
  })
}

/** The bot renames every assigned thread that is not archived after the template; failures go to the system log. */
export async function renameEmbassyThreads(): Promise<void> {
  await request(`${EMBASSIES}/thread-renames`, { method: 'POST' })
}

/** Only a creation that failed. */
export async function dismissEmbassyCreation(creationId: number): Promise<void> {
  await request(`${EMBASSIES}/creations/${creationId}`, { method: 'DELETE' })
}

export async function unassignEmbassyThread(threadId: string): Promise<void> {
  await request(`${EMBASSIES}/threads/${threadId}`, { method: 'DELETE' })
}

export async function addEmbassyMember(threadId: string, discordUserId: string): Promise<void> {
  await request(`${EMBASSIES}/threads/${threadId}/members`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ discordUserId }),
  })
}

export async function removeEmbassyMember(threadId: string, discordUserId: string): Promise<void> {
  await request(`${EMBASSIES}/threads/${threadId}/members/${discordUserId}`, {
    method: 'DELETE',
  })
}

/** Members of the Discord by name or ID, at most 20. */
export async function searchEmbassyMembers(query: string): Promise<MemberOption[]> {
  return (await request(`${EMBASSIES}/member-search?${new URLSearchParams({ query })}`)).json()
}

/** Parts of BITS an admin can switch off under "Verwaltung"; a feature is on until then. */
export type Feature = 'VERIFICATION' | 'ROLE_SYNC' | 'NICKNAMES' | 'PROFILE' | 'VOTES' | 'EMBASSIES'

export interface FeatureState {
  feature: Feature
  enabled: boolean
}

export async function getFeatures(): Promise<FeatureState[]> {
  return (await request('/web/features')).json()
}

export async function setFeature(feature: Feature, enabled: boolean): Promise<void> {
  await request(`/web/admin/features/${feature}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

const BOT_PRESENCE = '/web/admin/bot-presence'

export type BotStatus = 'ONLINE' | 'IDLE' | 'DO_NOT_DISTURB' | 'INVISIBLE'

/** `CUSTOM` is a status text of its own, without "Spielt" or the like in front. */
export type BotActivityType = 'PLAYING' | 'WATCHING' | 'LISTENING' | 'COMPETING' | 'CUSTOM'

/** Discord's limit for the text of an activity. */
export const MAX_ACTIVITY_TEXT_LENGTH = 128

export interface BotPresence {
  status: BotStatus
  /** `null` for no activity. */
  activity: { type: BotActivityType; text: string } | null
}

export async function getBotPresence(): Promise<BotPresence> {
  const presence = await (await request(BOT_PRESENCE)).json()
  // The backend leaves out fields with their default, i.e. online without activity.
  return {
    status: presence.status ?? 'ONLINE',
    activity: presence.activity ?? null,
  }
}

export async function setBotPresence(presence: BotPresence): Promise<void> {
  await request(BOT_PRESENCE, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(presence),
  })
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

const VOTES = '/web/votes'

export interface VoteAccess {
  /** Whether the user may see, start, end and cancel votes. */
  canManage: boolean
}

/** `COUNTING`: ended, the result is not posted yet. */
export type VoteStatus = 'OPEN' | 'COUNTING' | 'EVALUATED' | 'CANCELLED'

export interface Vote {
  id: number
  question: string
  /** Names as of the start; `null` if BITS did not know them. */
  roleName: string | null
  channelName: string | null
  /** Epoch milliseconds, like the other times. */
  startedAt: number
  startedBy: string
  /** `null` for a vote without end. */
  endsAt: number | null
  /** At which the vote ends early, `null` if it does not. */
  threshold: VoteThreshold | null
  /** Whether it ended because it reached `threshold`. */
  endedByThreshold: boolean
  closedAt: number | null
  /** `null` if it ended on its own. */
  closedBy: string | null
  status: VoteStatus
  /** Ballots so far; only for open votes. */
  voted: number | null
  /** Members of the role with a linked account, as the bot last reported; `null` until it did. */
  eligible: number | null
  /** Only for evaluated votes; the image is at `voteResultImageUrl(id)`. */
  resultText: string | null
}

/** Abstaining decides nothing, so a threshold is for Yes or No only. */
export type ThresholdChoice = 'YES' | 'NO'

/** The vote ends early once more than `percent` (1 to 99) of the eligible members chose `choice`. */
export interface VoteThreshold {
  percent: number
  choice: ThresholdChoice
}

export interface Votes {
  /** Newest first. */
  open: Vote[]
  /** Newest end first. */
  closed: Vote[]
}

export interface VoteOptions {
  /** Highest first. */
  roles: { id: string; name: string; color: number | null }[]
  /** Only those the bot may post in, in Discord's order. */
  channels: { id: string; name: string; category: string | null }[]
}

export interface CreateVote {
  question: string
  roleId: string
  channelId: string
  /** Epoch milliseconds, `null` for a vote without end. */
  endsAt: number | null
  /** Ends the vote early, whichever comes first with `endsAt`; `null` if it does not. */
  threshold: VoteThreshold | null
}

export async function getVoteAccess(): Promise<VoteAccess> {
  return (await request(`${VOTES}/access`)).json()
}

export async function getVotes(): Promise<Votes> {
  return (await request(VOTES)).json()
}

export async function getVoteOptions(): Promise<VoteOptions> {
  return (await request(`${VOTES}/options`)).json()
}

export async function createVote(vote: CreateVote): Promise<Vote> {
  const response = await request(VOTES, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(vote),
  })
  return response.json()
}

/** Ends the vote early, which starts the evaluation; `cancel` ends it without one. */
export async function closeVote(id: number, cancel: boolean): Promise<void> {
  await request(`${VOTES}/${id}/${cancel ? 'cancel' : 'end'}`, {
    method: 'POST',
  })
}

export function voteResultImageUrl(id: number): string {
  return `${VOTES}/${id}/result.png`
}

const VOTE_MANAGER_ROLES = '/web/admin/votes/manager-roles'

export async function getVoteManagerRoles(): Promise<string[]> {
  return (await (await request(VOTE_MANAGER_ROLES)).json()).roleIds
}

export async function setVoteManagerRoles(roleIds: string[]): Promise<void> {
  await request(VOTE_MANAGER_ROLES, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roleIds }),
  })
}

const RESULT_SETTINGS = '/web/admin/votes/result-settings'

/** Largest file the backend takes as background of the result image. */
export const MAX_BACKGROUND_BYTES = 5 * 1024 * 1024

export interface ResultSettings {
  /** Whether an admin uploaded a background; if not, it is the BITS eagle. */
  customBackground: boolean
  /** Epoch milliseconds of the last change; `null` while nothing was changed. */
  updatedAt: number | null
}

export interface PartyColors {
  /** `null` while no country is set under "Verwaltung"; `parties` is empty then. */
  countryName: string | null
  /** Largest first; `color` is `#RRGGBB`, `null` for the palette's. */
  parties: { id: string; name: string; color: string | null }[]
  /** `#RRGGBB`, `null` for the palette's grey. */
  noPartyColor: string | null
}

export async function getResultSettings(): Promise<ResultSettings> {
  return (await request(RESULT_SETTINGS)).json()
}

/** Sent as the raw body with the file's type; 413 if larger than [MAX_BACKGROUND_BYTES], 400 or 415 if no PNG or JPEG. */
export async function uploadResultBackground(file: File): Promise<void> {
  await request(`${RESULT_SETTINGS}/background`, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file,
  })
}

export async function resetResultBackground(): Promise<void> {
  await request(`${RESULT_SETTINGS}/background`, { method: 'DELETE' })
}

export async function getPartyColors(): Promise<PartyColors> {
  return (await request(`${RESULT_SETTINGS}/party-colors`)).json()
}

/** `colors` by WarEra party ID; parties left out get the palette's colour again. */
export async function setPartyColors(colors: Record<string, string>, noPartyColor: string | null): Promise<void> {
  await request(`${RESULT_SETTINGS}/party-colors`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ colors, noPartyColor }),
  })
}

/** [version] changes the URL, so the browser loads the preview again after a change. */
export function resultPreviewUrl(version: number): string {
  return `${RESULT_SETTINGS}/preview.png?v=${version}`
}

export const DISCORD_LOGIN_URL = '/auth/oauth2/authorization/discord'

export interface Account {
  /** `null` if the member has not linked a WarEra account. */
  link: { wareraUsername: string; verifiedAt: number } | null
  /** Epoch milliseconds of the last sync of all members; `null` if none is known. */
  lastSyncAt: number | null
}

export type AccountRoleOutcome = 'MET' | 'NOT_MET' | 'UNDECIDED'

export interface AccountRole {
  id: string
  name: string
  color: number | null
  outcome: AccountRoleOutcome
  /** The blocks of the rule of the role, any one of which is enough. */
  rules: { label: string; met: boolean | null; detail: string | null }[]
}

export interface AccountRoles {
  linked: boolean
  /** Every role BITS manages, highest first; judged with fresh WarEra data. */
  roles: AccountRole[]
}

const ACCOUNT = '/web/account'

/** A plain link downloads the file; the browser sends the session cookie along. */
export const ACCOUNT_EXPORT_URL = `${ACCOUNT}/export`

export async function getAccount(): Promise<Account> {
  return (await request(ACCOUNT)).json()
}

export async function getAccountRoles(): Promise<AccountRoles> {
  return (await request(`${ACCOUNT}/roles`)).json()
}

export async function unlinkAccount(): Promise<void> {
  await request(`${ACCOUNT}/link`, { method: 'DELETE' })
}
