import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import type { TopManagerRoles } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const managerRoles: TopManagerRoles = {
  roles: [
    { id: 'r-chair', name: 'Präsidium', color: 0x00ff00 },
    { id: 'r-citizen', name: 'Bürger', color: null },
  ],
  roleIds: ['r-chair'],
}

/** The admin page with every card empty except this one; [save] answers the PUT. */
function stub(save: (init?: RequestInit) => Response) {
  stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: true }),
    'GET /web/admin/role-sync/countries': () => json([]),
    'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    'GET /web/admin/bot-presence': () => json({}),
    'GET /web/admin/honeypot': () => json({ channelId: null, kicksPaused: false, channels: [] }),
    'GET /web/votes/options': () => json({ roles: [], channels: [] }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: [] }),
    'GET /web/admin/tops': () => json({ forums: [], selected: [] }),
    'GET /web/admin/tops/manager-roles': () => json(managerRoles),
    'PUT /web/admin/tops/manager-roles': save,
  })
}

async function card() {
  return (await screen.findByText('Wer TOPs verwalten darf')).closest('[data-slot=card]') as HTMLElement
}

describe('roles that may manage TOPs', () => {
  it('lets admins choose them on the TOPs tab of the admin page', async () => {
    const saved: unknown[] = []
    stub((init) => {
      saved.push(JSON.parse(String(init?.body)))
      return status(204)
    })
    renderApp('/admin?tab=tops')

    const roles = await card()
    expect(await within(roles).findByRole('checkbox', { name: 'Präsidium' })).toBeChecked()
    await userEvent.click(within(roles).getByRole('checkbox', { name: 'Bürger' }))
    await userEvent.click(within(roles).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ roleIds: ['r-chair', 'r-citizen'] }])
    await expectToast('success', 'Rollen gespeichert')
  })

  it('says so when a role is gone by the time it is saved', async () => {
    stub(() => status(400))
    renderApp('/admin?tab=tops')

    const roles = await card()
    await userEvent.click(await within(roles).findByRole('checkbox', { name: 'Bürger' }))
    await userEvent.click(within(roles).getByRole('button', { name: 'Speichern' }))

    await expectToast('error', 'Eine der Rollen gibt es nicht mehr.')
  })
})
