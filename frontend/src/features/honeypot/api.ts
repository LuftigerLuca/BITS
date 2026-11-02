import { request } from '@/lib/api'

const HONEYPOT = '/web/admin/honeypot'

export interface HoneypotChannel {
  id: string
  name: string
  category: string | null
  /** Whether the bot has what a honeypot takes there. */
  usable: boolean
}

export interface Honeypot {
  /** `null` while no honeypot is set. */
  channelId: string | null
  kicksPaused: boolean
  /** Text channels in the order of Discord. */
  channels: HoneypotChannel[]
}

export async function getHoneypot(): Promise<Honeypot> {
  return (await request(HONEYPOT)).json()
}

export async function setHoneypotChannel(channelId: string | null): Promise<void> {
  await request(HONEYPOT, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channelId }),
  })
}

export async function resumeHoneypotKicks(): Promise<void> {
  await request(`${HONEYPOT}/resume`, { method: 'POST' })
}
