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

    await userEvent.click(votes)

    await expectToast('success', 'Abstimmungen ausgeschaltet')
    expect(fetch).toHaveBeenCalledWith(
      '/web/admin/features/VOTES',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ enabled: false }) }),
    )
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Abstimmungen' })).not.toBeChecked())
    // Its page stays, so its settings can be prepared while it is paused.
    expect(screen.getByRole('link', { name: 'Abstimmungen' })).toBeInTheDocument()
  })

  it('says in a toast when the switch cannot be saved', async () => {
    stub([], () => status(500))
    renderApp('/admin')

    await userEvent.click(await screen.findByRole('switch', { name: 'Abstimmungen' }))

    await expectToast('error', 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.')
    expect(screen.getByRole('switch', { name: 'Abstimmungen' })).toBeChecked()
  })

  it('keeps the pages of features that are off and says so on them, with their settings still editable', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/features': () => features('ROLE_SYNC', 'EMBASSIES'),
      'GET /web/votes/access': () => json({ canManage: true }),
      'GET /web/admin/role-sync/rules': () => json([]),
      'GET /web/admin/role-sync/status-roles': () => json({ verified: null, unverified: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/role-sync/roles': () => json([]),
    })
    renderApp('/admin/roles')

    expect(await screen.findByRole('status')).toHaveTextContent('Diese Funktion ist gerade ausgeschaltet.')
    expect(screen.getByRole('heading', { name: 'Rollen' })).toBeInTheDocument()
    const navigation = screen.getByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).getByRole('link', { name: 'Rollen' })).toHaveAttribute('aria-current', 'page')
    expect(within(navigation).getByRole('link', { name: 'Botschaften' })).toBeInTheDocument()
  })

  it('shows no notice on the pages of features that are on', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/features': () => features('EMBASSIES'),
      'GET /web/votes/access': () => json({ canManage: true }),
      'GET /web/admin/role-sync/rules': () => json([]),
      'GET /web/admin/role-sync/status-roles': () => json({ verified: null, unverified: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/role-sync/roles': () => json([]),
    })
    renderApp('/admin/roles')

    expect(await screen.findByRole('heading', { name: 'Rollen' })).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
  })

  it('keeps the votes for members who may manage them while votes are off', async () => {
    stubFetch({
      'GET /web/me': () => json({ ...admin, role: 'USER' }),
      'GET /web/features': () => features('VOTES'),
      'GET /web/votes/access': () => json({ canManage: true }),
    })
    renderApp('/votes')

    expect(await screen.findByText(/Neue Abstimmungen lassen sich erst wieder starten/)).toBeInTheDocument()
    const navigation = screen.getByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).getByRole('link', { name: 'Abstimmungen' })).toHaveAttribute('aria-current', 'page')
  })
})
