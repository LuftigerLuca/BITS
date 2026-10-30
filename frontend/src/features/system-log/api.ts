import { request } from '@/lib/api'

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

export type SyncTrigger = 'HOURLY' | 'RULE_CHANGE' | 'ACCOUNT_CHANGE' | 'SETTING_CHANGE' | 'ADMIN_REQUEST'

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
