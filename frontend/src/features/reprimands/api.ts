import { request } from '@/lib/api'

const REPRIMANDS = '/web/admin/reprimands'
const SETTINGS = `${REPRIMANDS}/settings`

export interface Reprimand {
  id: number
  discordUserId: string
  /** As Discord showed the member then. */
  memberName: string
  reason: string
  actorName: string
  createdAt: number
  /** `null` while it counts. */
  withdrawnAt: number | null
  withdrawnBy: string | null
}

export interface ReprimandCount {
  discordUserId: string
  /** As of the newest reprimand. */
  memberName: string
  count: number
}

export interface Reprimands {
  /** The newest first, withdrawn ones too. */
  reprimands: Reprimand[]
  /** Reprimands that count per member, the most first. */
  counts: ReprimandCount[]
}

export interface ReprimandRole {
  id: string
  name: string
  color: number | null
}

export interface ReprimandSettings {
  /** The roles of the guild, highest first. */
  roles: ReprimandRole[]
  /** The roles whose members may reprimand besides the admins. */
  roleIds: string[]
  timeoutMinutes: number
}

export async function getReprimands(): Promise<Reprimands> {
  return (await request(REPRIMANDS)).json()
}

/** It no longer counts, and a timeout it still holds is lifted. */
export async function withdrawReprimand(id: number): Promise<void> {
  await request(`${REPRIMANDS}/${id}/withdraw`, { method: 'POST' })
}

export async function getReprimandSettings(): Promise<ReprimandSettings> {
  return (await request(SETTINGS)).json()
}

export async function setReprimandSettings(settings: { roleIds: string[]; timeoutMinutes: number }): Promise<void> {
  await request(SETTINGS, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
}
