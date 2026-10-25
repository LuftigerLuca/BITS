import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Me, Overview } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }
const countries = [
  { id: 'de', name: 'Germany' },
  { id: 'fr', name: 'France' },
]

function stub(overview: Overview, setCountry: (init?: RequestInit) => Response) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/role-sync/countries': () => json(countries),
    'GET /web/admin/overview': () => json(overview),
    'PUT /web/admin/overview/country': setCountry,
    'GET /web/votes/options': () => json({ roles: [], channels: [] }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: [] }),
    'GET /web/admin/votes/result-settings': () => json({ customBackground: false, updatedAt: null }),
    'GET /web/admin/votes/result-settings/party-colors': () =>
      json({ countryName: 'Germany', parties: [{ id: 'p1', name: 'Partei', color: null }], noPartyColor: null }),
  })
}

/** The admin page has more cards with a save button. */
function saveButton() {
  const card = screen.getByText('Startseite').closest('[data-slot=card]') as HTMLElement
  return within(card).getByRole('button', { name: 'Speichern' })
}

async function chooseCountry(name: string) {
  await userEvent.click(screen.getByLabelText('Land der Startseite'))
  await userEvent.click(await screen.findByRole('option', { name }))
}

describe('country of the start page', () => {
  it('shows the current country and saves a new one', async () => {
    let current = 'de'
    const fetch = stub(
      {
        members: 3,
        verifiedMembers: 1,
        country: { id: 'de', name: 'Germany', available: true, citizens: 982, activeCitizens: 925 },
      },
      (init) => {
        current = JSON.parse(String(init?.body)).countryId
        return json({ id: current, name: 'France' })
      },
    )
    renderApp('/admin')

    expect(await screen.findByText('Germany', { selector: '[data-slot=select-value]' })).toBeInTheDocument()
    expect(saveButton()).toBeDisabled()
    await chooseCountry('France')
    await userEvent.click(saveButton())

    await vi.waitFor(() => expect(current).toBe('fr'))
    await expectToast('success', 'Land gespeichert')
    // Loaded again after saving, so the start page shows the new country.
    await vi.waitFor(() =>
      expect(fetch.mock.calls.filter(([input]) => input.toString() === '/web/admin/overview')).toHaveLength(2),
    )
  })

  it('says when WarEra cannot be reached', async () => {
    stub({ members: null, verifiedMembers: null, country: null }, () => status(503))
    renderApp('/admin')

    await screen.findByText('Land wählen')
    await chooseCountry('Germany')
    await userEvent.click(saveButton())

    await expectToast('error', 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
