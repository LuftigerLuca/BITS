import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

/** The admin page with all its cards; the bot has [presence] and saving answers with [setPresence]. */
function stub(presence: unknown, setPresence: (init?: RequestInit) => Response = () => json({})) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/bot-presence': () => json(presence),
    'PUT /web/admin/bot-presence': setPresence,
    'GET /web/admin/role-sync/countries': () => json([]),
    'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    'GET /web/votes/options': () => json({ roles: [], channels: [] }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: [] }),
    'GET /web/admin/votes/result-settings': () => json({ customBackground: false, updatedAt: null }),
    'GET /web/admin/votes/result-settings/party-colors': () => json({ countryName: null, parties: [], noPartyColor: null }),
  })
}

function card() {
  return screen.getByText('Bot-Status').closest('[data-slot=card]') as HTMLElement
}

async function choose(label: string, option: string) {
  await userEvent.click(within(card()).getByLabelText(label))
  await userEvent.click(await screen.findByRole('option', { name: option }))
}

describe('bot presence', () => {
  it('shows online without activity until something is set, and saves status and activity', async () => {
    // The backend leaves out the defaults.
    const fetch = stub({})
    renderApp('/admin')

    expect(await screen.findByText('Online', { selector: '[data-slot=select-value]' })).toBeInTheDocument()
    expect(within(card()).getByText('Keine', { selector: '[data-slot=select-value]' })).toBeInTheDocument()
    expect(within(card()).queryByLabelText('Text der Aktivität')).not.toBeInTheDocument()
    const save = within(card()).getByRole('button', { name: 'Speichern' })
    expect(save).toBeDisabled()

    await choose('Status', 'Abwesend')
    await choose('Aktivität', 'Schaut …')
    // An activity needs a text.
    expect(save).toBeDisabled()
    await userEvent.type(within(card()).getByLabelText('Text der Aktivität'), ' WarEra ')
    await userEvent.click(save)

    await expectToast('success', 'Bot-Status gespeichert')
    expect(fetch).toHaveBeenCalledWith(
      '/web/admin/bot-presence',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ status: 'IDLE', activity: { type: 'WATCHING', text: 'WarEra' } }),
      }),
    )
  })

  it('shows a custom status and removes the activity', async () => {
    const fetch = stub({ status: 'DO_NOT_DISTURB', activity: { type: 'CUSTOM', text: 'Wartung' } })
    renderApp('/admin')

    expect(await screen.findByLabelText('Status-Text')).toHaveValue('Wartung')
    expect(within(card()).getByText('Bitte nicht stören', { selector: '[data-slot=select-value]' })).toBeInTheDocument()

    await choose('Aktivität', 'Keine')
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expectToast('success', 'Bot-Status gespeichert')
    expect(fetch).toHaveBeenCalledWith(
      '/web/admin/bot-presence',
      expect.objectContaining({ body: JSON.stringify({ status: 'DO_NOT_DISTURB', activity: null }) }),
    )
  })

  it('says in a toast when the text is refused', async () => {
    stub({}, () => status(400))
    renderApp('/admin')

    await screen.findByText('Online', { selector: '[data-slot=select-value]' })
    await choose('Aktivität', 'Spielt …')
    await userEvent.type(within(card()).getByLabelText('Text der Aktivität'), 'WarEra')
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expectToast('error', 'Der Text der Aktivität ist leer oder zu lang.')
  })
})
