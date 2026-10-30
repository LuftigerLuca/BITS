import type { GovernmentOffice } from '@/features/role-sync/api'
import { request } from '@/lib/api'

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
