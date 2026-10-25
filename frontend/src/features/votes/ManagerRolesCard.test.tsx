import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me, VoteOptions } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const options: VoteOptions = {
  roles: [
    { id: 'r-congress', name: 'Kongress', color: 0x00ff00 },
    { id: 'r-citizen', name: 'Bürger', color: null },
  ],
  channels: [],
}

describe('roles that may manage votes', () => {
  it('lets admins choose them on the admin page', async () => {
    const saved: unknown[] = []
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/votes/access': () => json({ canManage: true }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/votes/options': () => json(options),
      'GET /web/admin/votes/manager-roles': () => json({ roleIds: ['r-congress'] }),
      'PUT /web/admin/votes/manager-roles': (init?: RequestInit) => {
        saved.push(JSON.parse(String(init?.body)))
        return status(204)
      },
    })
    renderApp('/admin')

    const card = (await screen.findByText('Wer Abstimmungen verwalten darf')).closest('[data-slot=card]') as HTMLElement
    const congress = await within(card).findByRole('checkbox', { name: 'Kongress' })
    expect(congress).toBeChecked()
    await userEvent.click(within(card).getByRole('checkbox', { name: 'Bürger' }))
    await userEvent.click(within(card).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ roleIds: ['r-congress', 'r-citizen'] }])
    // Shown although the form starts over with the saved roles.
    await expectToast('success', 'Rollen gespeichert')
  })
})
