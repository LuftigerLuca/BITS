import type { AccountRole } from '@/features/account/api'
import type { ItemOutcome, LogEntryType, SyncTrigger } from '@/features/system-log/api'
import { request } from '@/lib/api'

const MEMBERS = '/web/admin/members'

export interface MemberSummary {
  discordUserId: string
  /** `null` if the bot has not reported the member. */
  discordName: string | null
  /** Name at the time of linking; `null` without a link. */
  wareraUsername: string | null
  /** `null` until the bot has reported the members since the backend started. */
  onServer: boolean | null
}

/** An entry about the member's account, or a change of a sync for them, which has an `outcome`. */
export interface MemberLogEvent {
  /** Epoch milliseconds. */
  occurredAt: number
  kind: LogEntryType
  syncTrigger: SyncTrigger | null
  role: string | null
  outcome: ItemOutcome | null
  reason: string | null
  wareraUsername: string | null
  /** Whether the member is the one the WarEra account was taken from. */
  previousAccount: boolean
}

export interface MemberDetail {
  discordUserId: string
  discordName: string | null
  onServer: boolean | null
  link: { wareraUserId: string; wareraUsername: string; verifiedAt: number } | null
  /** Newest first, only the last few. */
  recentLog: MemberLogEvent[]
}

export interface MemberRole extends AccountRole {
  /** Whether the member holds the role now; `null` if the bot has not reported it. */
  held: boolean | null
}

export interface MemberRoles {
  linked: boolean
  /** Every role BITS manages, highest first; judged with fresh WarEra data. */
  roles: MemberRole[]
}

/** By a Discord ID, or part of the Discord or WarEra name; at most 20. */
export async function searchMembers(query: string): Promise<MemberSummary[]> {
  return (await request(`${MEMBERS}?${new URLSearchParams({ query })}`)).json()
}

export async function getMember(discordUserId: string): Promise<MemberDetail> {
  return (await request(`${MEMBERS}/${encodeURIComponent(discordUserId)}`)).json()
}

/** Judged with fresh WarEra data like `/rollen`; 503 if WarEra cannot be reached. */
export async function getMemberRoles(discordUserId: string): Promise<MemberRoles> {
  return (await request(`${MEMBERS}/${encodeURIComponent(discordUserId)}/roles`)).json()
}

/** 409 while the role sync, nicknames and status roles are all off. */
export async function syncMember(discordUserId: string): Promise<void> {
  await request(`${MEMBERS}/${encodeURIComponent(discordUserId)}/sync`, { method: 'POST' })
}

/** Works like `/unverify`. */
export async function unlinkMember(discordUserId: string): Promise<void> {
  await request(`${MEMBERS}/${encodeURIComponent(discordUserId)}/link`, { method: 'DELETE' })
}
