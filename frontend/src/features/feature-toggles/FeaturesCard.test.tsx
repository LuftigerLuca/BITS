import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Feature, Me } from '@/lib/api'
import { features, json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

/** The admin page with all its cards, where [off] are switched off and switching answers with [setFeature]. */
function stub(off: Feature[], setFeature: (init?: RequestInit) => Response = () => status(204)) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/features': () => features(...off),
    'GET /web/votes/access': () => json({ canManage: true }),
    'PUT /web/admin/features/VOTES': setFeature,
    'GET /web/admin/role-sync/countries': () => json([]),
    'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    'GET /web/votes/options': () => json({ roles: [], channels: [] }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: [] }),
    'GET /web/admin/votes/result-settings': () => json({ customBackground: false, updatedAt: null }),
    'GET /web/admin/votes/result-settings/party-colors': () => json({ countryName: null, parties: [], noPartyColor: null }),
  })
}

function card() {
  return screen.getByText('Funktionen').closest('[data-slot=card]') as HTMLElement
}

describe('features', () => {
  it('shows a switch per feature and switches one off', async () => {
    const off: Feature[] = []
    const fetch = stub(off, (init) => {
      if (JSON.parse(String(init?.body)).enabled === false) off.push('VOTES')
      return status(204)
    })
    renderApp('/admin')

    const votes = await screen.findByRole('switch', { name: 'Abstimmungen' })
    expect(within(card()).getAllByRole('switch')).toHaveLength(6)
    expect(votes).toBeChecked()
    expect(await screen.findByRole('link', { name: 'Abstimmungen' })).toBeInTheDocument()

    await userEvent.click(votes)

    await expectToast('success', 'Abstimmungen ausgeschaltet')
    expect(fetch).toHaveBeenCalledWith(
      '/web/admin/features/VOTES',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ enabled: false }) }),
    )
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Abstimmungen' })).not.toBeChecked())
    // Its page leaves the navigation with it.
    expect(screen.queryByRole('link', { name: 'Abstimmungen' })).not.toBeInTheDocument()
  })

  it('says in a toast when the switch cannot be saved', async () => {
    stub([], () => status(500))
    renderApp('/admin')

    await userEvent.click(await screen.findByRole('switch', { name: 'Abstimmungen' }))

    await expectToast('error', 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.')
    expect(screen.getByRole('switch', { name: 'Abstimmungen' })).toBeChecked()
  })

  it('hides the pages of features that are off and sends their links back to the start', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/features': () => features('ROLE_SYNC', 'EMBASSIES'),
      'GET /web/votes/access': () => json({ canManage: true }),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    })
    renderApp('/admin/roles')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    const navigation = screen.getByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).getByRole('link', { name: 'Start' })).toHaveAttribute('aria-current', 'page')
    await within(navigation).findByRole('link', { name: 'Abstimmungen' })
    expect(within(navigation).queryByRole('link', { name: 'Rollen' })).not.toBeInTheDocument()
    expect(within(navigation).queryByRole('link', { name: 'Botschaften' })).not.toBeInTheDocument()
  })

  it('hides the votes from members who may manage them while votes are off', async () => {
    stubFetch({
      'GET /web/me': () => json({ ...admin, role: 'USER' }),
      'GET /web/features': () => features('VOTES'),
      'GET /web/votes/access': () => json({ canManage: true }),
    })
    renderApp('/votes')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await waitFor(() =>
      expect(within(navigation).getAllByRole('link').map((link) => link.textContent)).toEqual(['Start', 'Mein Account']),
    )
    expect(within(navigation).queryByText('Admin')).not.toBeInTheDocument()
  })
})
