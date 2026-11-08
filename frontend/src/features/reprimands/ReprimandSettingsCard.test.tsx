import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import type { ReprimandSettings } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const settings: ReprimandSettings = {
  roles: [
    { id: 'r-mod', name: 'Moderation', color: 0x00ff00 },
    { id: 'r-citizen', name: 'Bürger', color: null },
  ],
  roleIds: ['r-mod'],
  timeoutMinutes: 120,
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
    'GET /web/admin/tops/manager-roles': () => json({ roles: [], roleIds: [] }),
    'GET /web/admin/reprimands/settings': () => json(settings),
    'PUT /web/admin/reprimands/settings': save,
  })
}

async function card() {
  return (await screen.findByRole('heading', { name: 'Rügen', level: 2 })).closest('[data-slot=card]') as HTMLElement
}

describe('reprimand settings', () => {
  it('shows the timeout in the largest unit that fits and saves roles and timeout in minutes', async () => {
    const saved: unknown[] = []
    stub((init) => {
      saved.push(JSON.parse(String(init?.body)))
      return status(204)
    })
    renderApp('/admin?tab=ruegen')

    const settingsCard = await card()
    expect(await within(settingsCard).findByRole('checkbox', { name: 'Moderation' })).toBeChecked()
    expect(within(settingsCard).getByLabelText('Dauer')).toHaveValue(2)
    expect(within(settingsCard).getByRole('button', { name: 'Speichern' })).toBeDisabled()

    await userEvent.click(within(settingsCard).getByRole('checkbox', { name: 'Bürger' }))
    await userEvent.clear(within(settingsCard).getByLabelText('Dauer'))
    await userEvent.type(within(settingsCard).getByLabelText('Dauer'), '3')
    await userEvent.click(within(settingsCard).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ roleIds: ['r-mod', 'r-citizen'], timeoutMinutes: 180 }])
    await expectToast('success', 'Einstellungen gespeichert')
  })

  it('refuses a timeout beyond 28 days', async () => {
    stub(() => status(204))
    renderApp('/admin?tab=ruegen')

    const settingsCard = await card()
    const amount = await within(settingsCard).findByLabelText('Dauer')
    await userEvent.clear(amount)
    await userEvent.type(amount, '700')

    expect(within(settingsCard).getByText('Der Timeout muss zwischen 1 Minute und 28 Tagen liegen.')).toBeInTheDocument()
    expect(amount).toHaveAttribute('aria-invalid', 'true')
    expect(within(settingsCard).getByRole('button', { name: 'Speichern' })).toBeDisabled()
  })

  it('has no accessibility violations axe can find with the unit select open', async () => {
    stub(() => status(204))
    renderApp('/admin?tab=ruegen')

    await userEvent.click(await within(await card()).findByLabelText('Einheit'))
    await screen.findByRole('option', { name: 'Tage' })
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
