import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { features, json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

/** The admin page with all its cards filled. */
function stub() {
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
    'GET /web/admin/bot-presence': () => json({ status: 'ONLINE', activity: { type: 'PLAYING', text: 'WarEra' } }),
    'GET /web/admin/honeypot': () => json({ channelId: null, channels: [], kicksPaused: false }),
    'GET /web/votes/options': () =>
      json({
        roles: [{ id: 'r1', name: 'Bürger', color: null }],
        channels: [{ id: 'c1', name: 'abstimmungen', category: null }],
      }),
    'GET /web/admin/votes/manager-roles': () => json({ roleIds: ['r1'] }),
    'GET /web/admin/votes/result-settings': () => json({ customBackground: false, updatedAt: null }),
    'GET /web/admin/tops': () =>
      json({
        forums: [{ id: 'f1', name: 'tops', category: null, usable: true, tags: [{ id: 't1', name: 'Erledigt' }] }],
        selected: [{ forumId: 'f1', doneTagId: 't1' }],
      }),
    'GET /web/admin/votes/result-settings/party-colors': () =>
      json({ countryName: 'Germany', parties: [{ id: 'p1', name: 'Partei', color: '#ff0000' }], noPartyColor: null }),
  })
}

/** The headings of the cards on the tab that is open. */
function visibleCards() {
  return screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)
}

describe('admin page', () => {
  it.each([
    ['/admin', 'Funktionen'],
    ['/admin?tab=unbekannt', 'Funktionen'],
    ['/admin?tab=funktionen', 'Funktionen'],
    ['/admin?tab=server', 'Server'],
    ['/admin?tab=abstimmungen', 'Abstimmungen'],
  ])('opens %s on the tab %s', async (path, tab) => {
    stub()
    renderApp(path)

    expect(await screen.findByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getAllByRole('tab', { selected: true })).toHaveLength(1)
  })

  it('groups the cards by tab', async () => {
    stub()
    const router = renderApp('/admin')

    await screen.findByRole('tab', { name: 'Funktionen' })
    expect(visibleCards()).toEqual(['Funktionen'])

    await userEvent.click(screen.getByRole('tab', { name: 'Server' }))
    expect(visibleCards()).toEqual(['Startseite', 'Bot-Status', 'Honeypot'])
    expect(router.state.location.search).toBe('?tab=server')

    await userEvent.click(screen.getByRole('tab', { name: 'Abstimmungen' }))
    expect(visibleCards()).toEqual([
      'Wer Abstimmungen verwalten darf',
      'Bild der Auswertung',
      'Parteifarben',
      'TOP-Foren',
    ])
    expect(router.state.location.search).toBe('?tab=abstimmungen')
  })

  it('replaces the history entry on a switch, so that back leaves the page', async () => {
    stub()
    const router = renderApp('/admin')

    await userEvent.click(await screen.findByRole('tab', { name: 'Server' }))

    expect(router.state.historyAction).toBe('REPLACE')
    expect(router.state.location.search).toBe('?tab=server')
  })

  it('switches with the arrow keys and leaves the focus on the tab', async () => {
    stub()
    const router = renderApp('/admin')

    await userEvent.click(await screen.findByRole('tab', { name: 'Funktionen' }))
    await userEvent.keyboard('{ArrowRight}')

    const server = screen.getByRole('tab', { name: 'Server' })
    await waitFor(() => expect(server).toHaveAttribute('aria-selected', 'true'))
    expect(server).toHaveFocus()
    expect(router.state.location.search).toBe('?tab=server')
  })

  it('keeps unsaved input when switching tabs back and forth', async () => {
    stub()
    renderApp('/admin?tab=server')

    const text = await screen.findByLabelText('Text der Aktivität')
    await userEvent.type(text, ' 2')
    await userEvent.click(screen.getByRole('tab', { name: 'Funktionen' }))
    await userEvent.click(screen.getByRole('tab', { name: 'Server' }))

    expect(screen.getByLabelText('Text der Aktivität')).toHaveValue('WarEra 2')
  })

  it.each(['funktionen', 'server', 'abstimmungen'])(
    'has no accessibility violations axe can find on the tab %s',
    async (tab) => {
      stub()
      renderApp(`/admin?tab=${tab}`)
      await screen.findByText('Partei')
      await screen.findByLabelText('Erledigt-Tag in #tops')
      await screen.findByText('Bot-Status')
      await expectNoAxeViolations()
    },
  )

  it('has no accessibility violations axe can find with a select open', async () => {
    stub()
    renderApp('/admin?tab=server')

    await userEvent.click(await screen.findByLabelText('Land der Startseite'))
    await screen.findByRole('option', { name: 'Germany' })
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
