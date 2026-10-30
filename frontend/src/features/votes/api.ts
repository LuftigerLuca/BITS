import { request } from '@/lib/api'

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
