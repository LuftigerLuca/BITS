import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

describe('roles page', () => {
  it('is linked from the sidebar and shows the role rules', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/role-sync/roles': () => json([]),
      'GET /web/admin/role-sync/rules': () => json([]),
      'GET /web/admin/role-sync/status-roles': () => json({ verified: null, unverified: null }),
      'GET /web/admin/role-sync/removal-limit': () => json({ enabled: true }),
    })
    const router = renderApp('/admin')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(within(navigation).getByRole('link', { name: 'Rollen' }))

    expect(await screen.findByRole('heading', { name: 'Rollen' })).toBeInTheDocument()
    expect(await screen.findByText('Noch keine Regeln angelegt')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/roles')
  })

  it('keeps members out', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, role: 'USER' }) })

    const router = renderApp('/admin/roles')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })
})
