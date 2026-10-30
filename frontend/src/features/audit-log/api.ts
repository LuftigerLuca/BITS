import { request } from '@/lib/api'

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
  | 'REMOVAL_LIMIT_TOGGLED'
  | 'MEMBER_SYNC_REQUESTED'
  | 'MEMBER_UNLINKED'
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
