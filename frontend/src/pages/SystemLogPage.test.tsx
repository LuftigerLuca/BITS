import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { SystemLogEntry, SystemLogItem } from '@/features/system-log/api'
import type { Me } from '@/lib/api'
import { json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectNoAxeViolations } from '@/test/axe'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const run: SystemLogEntry = {
  id: 3,
  occurredAt: Date.UTC(2026, 0, 1, 12, 0),
  type: 'SYNC_RUN',
  category: 'SYNC',
  sync: {
    trigger: 'HOURLY',
    status: 'APPLIED',
    linkedCount: 2,
    memberCount: 2,
    staleMemberCount: 0,
    granted: 1,
    removed: 1,
    skipped: 0,
    failed: 0,
    nicknames: 0,
  },
  member: null,
  previousMember: null,
  wareraUsername: null,
  role: null,
  message: null,
  hasItems: true,
}

const relinked: SystemLogEntry = {
  ...run,
  id: 2,
  type: 'ACCOUNT_RELINKED',
  category: 'ACCOUNT',
  sync: null,
  member: { id: '2', name: 'Second' },
  previousMember: { id: '1', name: 'Luca' },
  wareraUsername: 'Luftiger',
  hasItems: false,
}

const unanswered: SystemLogEntry = { ...run, id: 1, sync: { ...run.sync!, status: 'NO_RESPONSE', memberCount: null }, hasItems: false }

const items: SystemLogItem[] = [
  { id: 1, member: { id: '1', name: 'Luca' }, role: { id: 'r-veteran', name: 'Veteran' }, outcome: 'GRANTED', reason: null },
  { id: 2, member: { id: '2', name: null }, role: { id: 'r-citizen', name: 'Bürger' }, outcome: 'REMOVED', reason: null },
  { id: 3, member: { id: '1', name: 'Luca' }, role: null, outcome: 'NICKNAME_SET', reason: 'Nickname: Luftiger' },
]

/** The query strings the page sent to the system log; the fetch stub matches by path only. */
function systemLogCalls() {
  const fetch = globalThis.fetch as unknown as { mock: { calls: [RequestInfo | URL][] } }
  return fetch.mock.calls
    .map(([input]) => new URL(input.toString(), 'http://localhost'))
    .filter((url) => url.pathname === '/web/admin/system-log')
    .map((url) => url.searchParams.toString())
}

function stubLog(entries: SystemLogEntry[] = [run, relinked, unanswered]) {
  stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/system-log': () => json({ entries, nextBefore: null }),
    'GET /web/admin/system-log/roles': () => json([{ id: 'r-citizen', name: 'Bürger' }]),
    'GET /web/admin/system-log/3/items': () => json(items),
  })
}

describe('system log page', () => {
  it('describes runs, account events and runs the bot did not answer', async () => {
    stubLog()

    renderApp('/admin/system-log')

    expect(
      await screen.findAllByText('Sync stündlich: 2 Mitglieder · 1 vergeben · 1 entzogen · 0 übersprungen · 0 fehlgeschlagen'),
    ).toHaveLength(1)
    expect(screen.getByText('Second hat den WarEra-Account Luftiger von Luca übernommen.')).toBeInTheDocument()
    expect(screen.getByText('Keine Rückmeldung vom Bot')).toBeInTheDocument()
  })

  it('counts nicknames only for runs that changed some', async () => {
    stubLog([{ ...run, sync: { ...run.sync!, trigger: 'ACCOUNT_CHANGE', granted: 0, removed: 0, nicknames: 2 } }])

    renderApp('/admin/system-log')

    expect(
      await screen.findByText(
        'Sync nach Verknüpfung: 2 Mitglieder · 0 vergeben · 0 entzogen · 0 übersprungen · 0 fehlgeschlagen · 2 Nicknames',
      ),
    ).toBeInTheDocument()
  })

  it('expands a run to show the role changes', async () => {
    stubLog()
    renderApp('/admin/system-log')

    await userEvent.click(await screen.findByRole('button', { name: 'Details anzeigen' }))

    expect(await screen.findByText('Veteran')).toBeInTheDocument()
    expect(screen.getByText('Vergeben')).toBeInTheDocument()
    expect(screen.getByText('Nickname gesetzt')).toBeInTheDocument()
    expect(screen.getByText('Nickname: Luftiger')).toBeInTheDocument()
    // A member whose name BITS never saw is shown by ID.
    expect(screen.getByRole('cell', { name: '2' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Details ausblenden' })).toHaveAttribute('aria-expanded', 'true')
  })

  it('asks the backend for the chosen type, role and member', async () => {
    stubLog([])
    renderApp('/admin/system-log')
    expect(await screen.findByText('Noch keine Einträge')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('Typ'))
    await userEvent.click(await screen.findByRole('option', { name: 'Fehler' }))
    await userEvent.click(screen.getByLabelText('Rolle'))
    await userEvent.click(await screen.findByRole('option', { name: 'Bürger' }))
    await userEvent.type(screen.getByLabelText('Mitglied'), 'Luca')
    await userEvent.click(screen.getByRole('button', { name: 'Suchen' }))

    expect(await screen.findByText('Keine Einträge für diesen Filter')).toBeInTheDocument()
    expect(systemLogCalls().at(-1)).toBe('category=ERROR&member=Luca&roleId=r-citizen')
  })

  it('filters by the member a link names', async () => {
    stubLog([])
    renderApp('/admin/system-log?member=1')

    expect(await screen.findByText('Keine Einträge für diesen Filter')).toBeInTheDocument()
    expect(screen.getByLabelText('Mitglied')).toHaveValue('1')
    expect(systemLogCalls().at(-1)).toBe('member=1')
  })

  it('is linked from the sidebar', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/system-log': () => json({ entries: [], nextBefore: null }),
      'GET /web/admin/system-log/roles': () => json([]),
    })
    const router = renderApp('/admin')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(within(navigation).getByRole('link', { name: 'System-Log' }))

    expect(await screen.findByRole('heading', { name: 'System-Log' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/system-log')
  })

  it('keeps members out', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, role: 'USER' }) })

    const router = renderApp('/admin/system-log')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stubLog()
    renderApp('/admin/system-log')
    await userEvent.click(await screen.findByRole('button', { name: 'Details anzeigen' }))
    await screen.findByText('Veteran')

    await expectNoAxeViolations()
  })
})
