import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Embassies, Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const embassies: Embassies = {
  ownCountryId: 'de',
  settings: { offices: ['PRESIDENT', 'MINISTER_OF_FOREIGN_AFFAIRS'], ambassadorRoleId: 'r-amb', hostRoleIds: [] },
  roles: [
    { id: 'r-amb', name: 'Botschafter', color: null },
    { id: 'r-gov', name: 'Regierung', color: 0x00ff00 },
  ],
  threads: [
    {
      threadId: 't-fr',
      threadName: 'botschaft-frankreich',
      channelName: 'diplomatie',
      countryId: 'fr',
      countryName: 'France',
      missing: false,
      members: [
        { discordUserId: '3', name: 'Minister', reason: 'Außenminister von France' },
        { discordUserId: '5', name: 'Guest', reason: 'Manuell hinzugefügt' },
      ],
      manualMembers: [{ discordUserId: '5', name: 'Guest' }],
    },
  ],
  availableThreads: [{ id: 't-pl', name: 'botschaft-polen', channelName: 'diplomatie', archived: true, manageable: true }],
}

const countries = [
  { id: 'de', name: 'Germany' },
  { id: 'fr', name: 'France' },
  { id: 'pl', name: 'Poland' },
]

function stub(routes: Record<string, (init?: RequestInit) => Response> = {}) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/embassies': () => json(embassies),
    'GET /web/admin/role-sync/countries': () => json(countries),
    ...routes,
  })
}

function bodies(calls: unknown[]) {
  return (init?: RequestInit) => {
    calls.push(init?.body ? JSON.parse(String(init.body)) : null)
    return status(204)
  }
}

describe('embassies', () => {
  it('shows who is in each thread and why', async () => {
    stub()
    renderApp('/admin/embassies')

    const section = await screen.findByRole('region', { name: 'Botschaft France' })
    expect(within(section).getByText('#diplomatie › botschaft-frankreich')).toBeInTheDocument()
    expect(within(section).getByRole('row', { name: 'Minister Außenminister von France' })).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Guest aus France entfernen' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Botschaften' })).toHaveAttribute('aria-current', 'page')
  })

  it('assigns a free thread to a country that has none yet', async () => {
    const assigned: unknown[] = []
    stub({ 'POST /web/admin/embassies/threads': bodies(assigned) })
    renderApp('/admin/embassies')

    await userEvent.click(await screen.findByLabelText('Thread'))
    await userEvent.click(await screen.findByRole('option', { name: '#diplomatie › botschaft-polen (archiviert)' }))
    await userEvent.click(screen.getByLabelText('Land'))
    // The own country and those with a thread are not offered.
    expect(screen.queryByRole('option', { name: 'Germany' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'France' })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('option', { name: 'Poland' }))
    await userEvent.click(screen.getByRole('button', { name: 'Zuordnen' }))

    await expect.poll(() => assigned).toEqual([{ threadId: 't-pl', countryId: 'pl' }])
    await expectToast('success', 'Thread zugeordnet')
  })

  it('adds a member found by name and removes one added by hand', async () => {
    const added: unknown[] = []
    const removed: unknown[] = []
    stub({
      'GET /web/admin/embassies/member-search': () => json([{ discordUserId: '7', name: 'Diplomat' }]),
      'POST /web/admin/embassies/threads/t-fr/members': bodies(added),
      'DELETE /web/admin/embassies/threads/t-fr/members/5': bodies(removed),
    })
    renderApp('/admin/embassies')

    await userEvent.type(await screen.findByLabelText('Mitglied hinzufügen'), 'Dipl')
    await userEvent.click(await screen.findByRole('button', { name: 'Diplomat zu France hinzufügen' }))
    await expect.poll(() => added).toEqual([{ discordUserId: '7' }])

    await userEvent.click(screen.getByRole('button', { name: 'Guest aus France entfernen' }))
    await expect.poll(() => removed).toHaveLength(1)
    await expectToast('success', 'Mitglied entfernt')
  })

  it('saves offices, ambassador role and host roles', async () => {
    const saved: unknown[] = []
    stub({ 'PUT /web/admin/embassies/settings': bodies(saved) })
    renderApp('/admin/embassies')

    const card = (await screen.findByText('Einstellungen')).closest('[data-slot=card]') as HTMLElement
    const save = within(card).getByRole('button', { name: 'Speichern' })
    expect(save).toBeDisabled()
    await userEvent.click(within(card).getByRole('checkbox', { name: 'Vizepräsident' }))
    await userEvent.click(within(card).getByRole('checkbox', { name: 'Regierung' }))
    await userEvent.click(save)

    await expect
      .poll(() => saved)
      .toEqual([
        {
          offices: ['PRESIDENT', 'VICE_PRESIDENT', 'MINISTER_OF_FOREIGN_AFFAIRS'],
          ambassadorRoleId: 'r-amb',
          hostRoleIds: ['r-gov'],
        },
      ])
  })

  it('says why an assignment failed', async () => {
    stub({ 'POST /web/admin/embassies/threads': () => status(503) })
    renderApp('/admin/embassies')

    await userEvent.click(await screen.findByLabelText('Thread'))
    await userEvent.click(await screen.findByRole('option', { name: '#diplomatie › botschaft-polen (archiviert)' }))
    await userEvent.click(screen.getByLabelText('Land'))
    await userEvent.click(await screen.findByRole('option', { name: 'Poland' }))
    await userEvent.click(screen.getByRole('button', { name: 'Zuordnen' }))

    await expectToast('error', 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.')
  })
})
