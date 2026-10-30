import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it } from 'vitest'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { features, json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

describe('admin page', () => {
  it('has no accessibility violations axe can find, also with a select open', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/features': () => features(),
      'GET /web/votes/access': () => json({ canManage: true }),
      'GET /web/admin/role-sync/countries': () => json([{ id: 'de', name: 'Germany' }]),
      'GET /web/admin/overview': () =>
        json({
          members: 10,
          verifiedMembers: 5,
          country: { id: 'de', name: 'Germany', available: true, citizens: 100, activeCitizens: 50 },
        }),
      'GET /web/admin/bot-presence': () => json({}),
      'GET /web/votes/options': () =>
        json({
          roles: [{ id: 'r1', name: 'Bürger', color: null }],
          channels: [{ id: 'c1', name: 'abstimmungen', category: null }],
        }),
      'GET /web/admin/votes/manager-roles': () => json({ roleIds: ['r1'] }),
      'GET /web/admin/votes/result-settings': () => json({ customBackground: false, updatedAt: null }),
      'GET /web/admin/votes/result-settings/party-colors': () =>
        json({ countryName: 'Germany', parties: [{ id: 'p1', name: 'Partei', color: '#ff0000' }], noPartyColor: null }),
    })
    renderApp('/admin')
    await screen.findByText('Partei')
    await expectNoAxeViolations()

    await userEvent.click(screen.getByLabelText('Land der Startseite'))
    await screen.findByRole('option', { name: 'Germany' })
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
