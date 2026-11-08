import { request } from '@/lib/api'

const VERIFY_PANEL = '/web/admin/verify-panel'

export interface VerifyPanelChannel {
  id: string
  name: string
  category: string | null
  /** Whether the bot may post its message there. */
  usable: boolean
}

export interface VerifyPanel {
  /** `null` while no channel is set. */
  channelId: string | null
  /** Text channels in the order of Discord. */
  channels: VerifyPanelChannel[]
}

export async function getVerifyPanel(): Promise<VerifyPanel> {
  return (await request(VERIFY_PANEL)).json()
}

export async function setVerifyPanelChannel(channelId: string | null): Promise<void> {
  await request(VERIFY_PANEL, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channelId }),
  })
}
