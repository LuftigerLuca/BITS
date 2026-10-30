import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import { expectNoAxeViolations } from '@/test/axe'
import type { Embassies } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const embassies: Embassies = {
  ownCountryId: 'de',
  settings: {
    offices: ['PRESIDENT', 'MINISTER_OF_FOREIGN_AFFAIRS'],
    ambassadorRoleId: 'r-amb',
    hostRoleIds: [],
    channelId: 'c-1',
    threadNameTemplate: '🏛️ Botschaft {land}',
  },
  roles: [
    { id: 'r-amb', name: 'Botschafter', color: null },
    { id: 'r-gov', name: 'Regierung', color: 0x00ff00 },
  ],
  channels: [
    { id: 'c-1', name: 'diplomatie', category: 'Außen', threadsCreatable: true },
    { id: 'c-2', name: 'ankündigungen', category: null, threadsCreatable: false },
  ],
  threads: [
    {
      threadId: 't-fr',
      threadName: 'botschaft-frankreich',
      channelName: 'diplomatie',
      countryId: 'fr',
      countryName: 'France',
      missing: false,
      renameSkipped: false,
      members: [
        { discordUserId: '3', name: 'Minister', reason: 'Außenminister von France' },
        { discordUserId: '5', name: 'Guest', reason: 'Manuell hinzugefügt' },
      ],
      manualMembers: [{ discordUserId: '5', name: 'Guest' }],
    },
  ],
  availableThreads: [
    { id: 't-pl', name: 'botschaft-polen', channelName: 'diplomatie', archived: true, manageable: true },
  ],
  creations: [],
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
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/verliert es die Botschafter-Rolle/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Entfernen' }))
    await expect.poll(() => removed).toHaveLength(1)
    await expectToast('success', 'Mitglied entfernt')
  })

  it('unassigns a thread only after asking, since its ambassadors may lose their role', async () => {
    const removed: unknown[] = []
    stub({ 'DELETE /web/admin/embassies/threads/t-fr': bodies(removed) })
    renderApp('/admin/embassies')

    const section = await screen.findByRole('region', { name: 'Botschaft France' })
    await userEvent.click(within(section).getByRole('button', { name: 'Zuordnung aufheben' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/verliert die Botschafter-Rolle/)).toBeInTheDocument()
    expect(removed).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Zuordnung aufheben' }))
    await expect.poll(() => removed).toHaveLength(1)
    await expectToast('success', 'Zuordnung aufgehoben')
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
          channelId: 'c-1',
          threadNameTemplate: '🏛️ Botschaft {land}',
        },
      ])
  })

  it('saves the thread name template only with {land} exactly once', async () => {
    const saved: unknown[] = []
    stub({ 'PUT /web/admin/embassies/settings': bodies(saved) })
    renderApp('/admin/embassies')

    const name = await screen.findByLabelText('Thread-Name')
    const save = screen.getByRole('button', { name: 'Speichern' })
    await userEvent.clear(name)
    expect(screen.getByText('Der Thread-Name darf nicht leer sein.')).toBeInTheDocument()
    await userEvent.type(name, 'Botschaft')
    expect(screen.getByText('Der Thread-Name muss {land} genau einmal enthalten.')).toBeInTheDocument()
    expect(name).toHaveAttribute('aria-invalid', 'true')
    expect(name).toHaveAccessibleDescription('Der Thread-Name muss {land} genau einmal enthalten.')
    expect(save).toBeDisabled()
    await userEvent.type(name, ' {{land}{{land}')
    expect(save).toBeDisabled()
    await userEvent.clear(name)
    await userEvent.type(name, 'Botschaft – {{land}')
    await userEvent.click(save)

    await expect.poll(() => saved).toMatchObject([{ threadNameTemplate: 'Botschaft – {land}' }])
  })

  it('has the bot rename the threads once confirmed and marks archived ones it skips', async () => {
    const renamed: unknown[] = []
    stub({
      'GET /web/admin/embassies': () =>
        json({ ...embassies, threads: embassies.threads.map((t) => ({ ...t, renameSkipped: true })) }),
      'POST /web/admin/embassies/thread-renames': bodies(renamed),
    })
    renderApp('/admin/embassies')

    const section = await screen.findByRole('region', { name: 'Botschaft France' })
    expect(within(section).getByText('Archiviert, beim Umbenennen übersprungen')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Bestehende umbenennen' }))
    expect(renamed).toEqual([])
    await userEvent.click(await screen.findByRole('button', { name: 'Umbenennen' }))

    await expect.poll(() => renamed).toEqual([null])
    await expectToast('success', 'Threads werden umbenannt')
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

  it('says when a member may not see the channel of the thread', async () => {
    stub({
      'GET /web/admin/embassies/member-search': () => json([{ discordUserId: '7', name: 'Diplomat' }]),
      'POST /web/admin/embassies/threads/t-fr/members': () => status(422),
    })
    renderApp('/admin/embassies')

    await userEvent.type(await screen.findByLabelText('Mitglied hinzufügen'), 'Dipl')
    await userEvent.click(await screen.findByRole('button', { name: 'Diplomat zu France hinzufügen' }))

    await expectToast(
      'error',
      'Das Mitglied darf den Kanal des Threads in Discord nicht sehen, auch nicht mit der Botschafter-Rolle. Erlaube der Botschafter-Rolle dort „Kanal ansehen“.',
    )
  })

  it('has the bot create a thread with the name from the dialog', async () => {
    const created: unknown[] = []
    stub({ 'POST /web/admin/embassies/creations': bodies(created) })
    renderApp('/admin/embassies')

    await userEvent.click(await screen.findByLabelText('Land'))
    await userEvent.click(await screen.findByRole('option', { name: 'Poland' }))
    await userEvent.click(screen.getByRole('button', { name: 'Automatisch anlegen' }))
    const name = await screen.findByLabelText('Name')
    expect(name).toHaveValue('🏛️ Botschaft Poland')
    await userEvent.clear(name)
    await userEvent.type(name, 'Botschaft Polen')
    await userEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await expect.poll(() => created).toEqual([{ countryId: 'pl', threadName: 'Botschaft Polen' }])
    await expectToast('success', 'Thread wird angelegt')
  })

  it('offers creating a thread only once an embassy channel is set', async () => {
    stub({
      'GET /web/admin/embassies': () => json({ ...embassies, settings: { ...embassies.settings, channelId: null } }),
    })
    renderApp('/admin/embassies')

    await userEvent.click(await screen.findByLabelText('Land'))
    await userEvent.click(await screen.findByRole('option', { name: 'Poland' }))
    expect(screen.getByRole('button', { name: 'Automatisch anlegen' })).toBeDisabled()
    expect(
      screen.getByText('Zum automatischen Anlegen wähle unter „Einstellungen“ einen Botschafts-Kanal.'),
    ).toBeInTheDocument()
  })

  it('shows threads being created and why one failed, which can be dismissed', async () => {
    const dismissed: unknown[] = []
    stub({
      'GET /web/admin/embassies': () =>
        json({
          ...embassies,
          creations: [
            { id: 1, countryId: 'it', countryName: 'Italy', threadName: 'Botschaft Italy', failure: null },
            {
              id: 2,
              countryId: 'pl',
              countryName: 'Poland',
              threadName: 'Botschaft Poland',
              failure: 'Discord hat das Anlegen abgelehnt.',
            },
          ],
        }),
      'DELETE /web/admin/embassies/creations/2': bodies(dismissed),
    })
    renderApp('/admin/embassies')

    const italy = await screen.findByRole('region', { name: 'Botschaft Italy' })
    expect(within(italy).getByText('Wird angelegt …')).toBeInTheDocument()
    const poland = screen.getByRole('region', { name: 'Botschaft Poland' })
    expect(within(poland).getByText('Discord hat das Anlegen abgelehnt.')).toBeInTheDocument()
    expect(within(poland).getByRole('button', { name: 'Automatisch anlegen' })).toBeEnabled()

    await userEvent.click(within(poland).getByRole('button', { name: 'Verwerfen' }))
    await expect.poll(() => dismissed).toHaveLength(1)
  })

  it('says when the bot may not create threads in the embassy channel', async () => {
    stub({ 'PUT /web/admin/embassies/settings': () => status(422) })
    renderApp('/admin/embassies')

    await userEvent.click(await screen.findByLabelText('Botschafts-Kanal'))
    await userEvent.click(await screen.findByRole('option', { name: '#ankündigungen – Bot fehlen Rechte' }))
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await expectToast(
      'error',
      'Dem Bot fehlen im Botschafts-Kanal die Rechte „Private Threads erstellen“, „Threads verwalten“ oder „Nachrichten in Threads senden“.',
    )
  })

  it('has no accessibility violations axe can find, also with a dialog open', async () => {
    stub()
    renderApp('/admin/embassies')
    await screen.findByRole('region', { name: 'Botschaft France' })
    await expectNoAxeViolations()

    await userEvent.click(screen.getByRole('button', { name: 'Guest aus France entfernen' }))
    await screen.findByRole('alertdialog')
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
