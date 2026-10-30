import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { PartyColors } from './api'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

function stub(partyColors: () => Response, routes: Record<string, (init?: RequestInit) => Response> = {}) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/role-sync/countries': () => json([]),
    'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    'GET /web/admin/bot-presence': () => json({}),
    'GET /web/votes/options': () => json({ roles: [], channels: [] }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: [] }),
    'GET /web/admin/votes/result-settings': () => json({ customBackground: false, updatedAt: null }),
    'GET /web/admin/votes/result-settings/party-colors': partyColors,
    ...routes,
  })
}

async function card() {
  return (await screen.findByText('Parteifarben')).closest('[data-slot=card]') as HTMLElement
}

describe('party colours', () => {
  it('gives the parties of the country a colour of their own and the others back the palette', async () => {
    const saved: PartyColors = {
      countryName: 'Germany',
      parties: [
        { id: 'p1', name: 'Deutsche Partei', color: '#FF0000' },
        { id: 'p2', name: 'Kleine Partei', color: null },
      ],
      noPartyColor: null,
    }
    let request: unknown
    stub(() => json(saved), {
      'PUT /web/admin/votes/result-settings/party-colors': (init) => {
        request = JSON.parse(String(init?.body))
        return status(204)
      },
    })
    renderApp('/admin')

    const colors = within(await card())
    expect(await colors.findByText('Parteien in Germany')).toBeInTheDocument()
    expect(colors.getByRole('button', { name: 'Speichern' })).toBeDisabled()

    // Only parties with a colour of their own have a colour field.
    expect(colors.getByLabelText('Farbe von Deutsche Partei')).toBeInTheDocument()
    expect(colors.queryByLabelText('Farbe von Kleine Partei')).not.toBeInTheDocument()
    const [own, small, noParty] = colors.getAllByRole('checkbox', { name: 'Eigene Farbe' })
    expect(own).toBeChecked()
    expect(small).not.toBeChecked()

    await userEvent.click(small)
    fireEvent.input(colors.getByLabelText('Farbe von Kleine Partei'), { target: { value: '#00ff00' } })
    await userEvent.click(noParty)
    fireEvent.input(colors.getByLabelText('Farbe von Parteilos'), { target: { value: '#0000ff' } })
    await userEvent.click(own)
    expect(colors.queryByLabelText('Farbe von Deutsche Partei')).not.toBeInTheDocument()
    await userEvent.click(colors.getByRole('button', { name: 'Speichern' }))

    await vi.waitFor(() => expect(request).toEqual({ colors: { p2: '#00FF00' }, noPartyColor: '#0000FF' }))
    await expectToast('success', 'Parteifarben gespeichert')
  })

  it('asks for a country instead of listing parties', async () => {
    stub(() => json({ countryName: null, parties: [], noPartyColor: null }))
    renderApp('/admin')

    const colors = within(await card())
    expect(
      await colors.findByText('Lege unter „Startseite“ ein Land fest, um die Farben seiner Parteien einzustellen.'),
    ).toBeInTheDocument()
    expect(colors.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
  })

  it('says in a toast when the colours cannot be saved', async () => {
    stub(() => json({ countryName: 'Germany', parties: [], noPartyColor: null }), {
      'PUT /web/admin/votes/result-settings/party-colors': () => status(409),
    })
    renderApp('/admin')

    const colors = within(await card())
    await userEvent.click(await colors.findByRole('checkbox', { name: 'Eigene Farbe' }))
    await userEvent.click(colors.getByRole('button', { name: 'Speichern' }))

    await expectToast('error', 'Unter „Startseite“ ist kein Land mehr eingestellt.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('says when WarEra cannot be reached', async () => {
    stub(() => status(503))
    renderApp('/admin')

    const colors = within(await card())
    expect(
      await colors.findByText('WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'),
    ).toBeInTheDocument()
  })
})
