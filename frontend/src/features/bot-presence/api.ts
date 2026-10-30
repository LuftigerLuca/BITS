import { request } from '@/lib/api'

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
