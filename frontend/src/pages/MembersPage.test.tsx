import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import type { Me, MemberDetail, MemberRoles, MemberSummary } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = {
  name: 'Admina',
  avatarUrl: null,
  role: 'ADMIN',
  wareraUsername: null,
  discordUser: true,
}

const luca: MemberSummary = {
  discordUserId: '1',
  discordName: 'Luca',
  wareraUsername: 'Luftiger',
  onServer: true,
}

// Local times, so the page shows the same in every time zone.
const detail: MemberDetail = {
  discordUserId: '1',
  discordName: 'Luca',
  onServer: true,
  link: {
    wareraUserId: 'w1',
    wareraUsername: 'Luftiger',
    verifiedAt: new Date(2025, 11, 24, 18, 0).getTime(),
  },
  recentLog: [
    {
      occurredAt: new Date(2026, 0, 1, 11, 0).getTime(),
      kind: 'SYNC_RUN',
      syncTrigger: 'ADMIN_REQUEST',
      role: 'Veteran',
      outcome: 'GRANTED',
      reason: 'BITS: Level 30',
      wareraUsername: null,
      previousAccount: false,
    },
    {
      occurredAt: new Date(2025, 11, 24, 18, 0).getTime(),
      kind: 'ACCOUNT_LINKED',
      syncTrigger: null,
      role: null,
      outcome: null,
      reason: null,
      wareraUsername: 'Luftiger',
      previousAccount: false,
    },
  ],
}

const roles: MemberRoles = {
  linked: true,
  roles: [
    {
      id: 'r-citizen',
      name: 'Bürger Deutschland',
      color: 0xff0000,
      outcome: 'MET',
      held: false,
      rules: [{ label: 'Staatsbürgerschaft Germany', met: true, detail: null }],
    },
  ],
}

function rows(table: HTMLElement) {
  return within(table)
    .getAllByRole('row')
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    )
}

describe('members page', () => {
  it('is linked from the sidebar, finds a member and shows link, roles and recent log', async () => {
    const fetch = stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/members': () => json([luca]),
      'GET /web/admin/members/1': () => json(detail),
      'GET /web/admin/members/1/roles': () => json(roles),
    })
    const router = renderApp('/admin/roles')

    const navigation = await screen.findByRole('navigation', {
      name: 'Hauptnavigation',
    })
    await userEvent.click(within(navigation).getByRole('link', { name: 'Mitglieder' }))
    expect(await screen.findByRole('heading', { name: 'Mitglieder' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Name oder Discord-ID'), 'Luftig')
    await userEvent.click(screen.getByRole('button', { name: 'Suchen' }))
    await userEvent.click(await screen.findByRole('link', { name: 'Luca' }))

    expect(router.state.location.search).toBe('?id=1')
    expect(fetch).toHaveBeenCalledWith('/web/admin/members?query=Luftig', expect.anything())
    const terms = (await screen.findAllByRole('term')).map((term) => [term.textContent, term.nextSibling?.textContent])
    expect(terms).toEqual([
      ['Discord-ID', '1'],
      ['Auf dem Server', 'ja'],
      ['WarEra-Account', 'Luftiger'],
      ['Verknüpft am', '24.12.2025, 18:00'],
    ])
    await screen.findByText('Bürger Deutschland')
    const [, rolesTable, logTable] = screen.getAllByRole('table')
    expect(rows(rolesTable)).toEqual([['Bürger Deutschland', 'Erfüllt', 'hat sie nicht', 'Staatsbürgerschaft Germany']])
    expect(rows(logTable)).toEqual([
      ['01.01.2026, 11:00', 'Sync auf Anfrage eines Admins: Veteran vergeben (BITS: Level 30)'],
      ['24.12.2025, 18:00', 'WarEra-Account Luftiger verknüpft'],
    ])
    expect(screen.getByRole('link', { name: 'Alle Einträge im System-Log' })).toHaveAttribute(
      'href',
      '/admin/system-log?member=1',
    )
  })

  it('starts a sync and says when there is nothing to sync', async () => {
    let enabled = true
    const fetch = stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/members/1': () => json(detail),
      'GET /web/admin/members/1/roles': () => json(roles),
      'POST /web/admin/members/1/sync': () => status(enabled ? 202 : 409),
    })
    renderApp('/admin/members?id=1')

    await userEvent.click(await screen.findByRole('button', { name: 'Jetzt abgleichen' }))
    await expectToast('success', 'Abgleich angestoßen')
    expect(fetch).toHaveBeenCalledWith('/web/admin/members/1/sync', expect.objectContaining({ method: 'POST' }))

    enabled = false
    await userEvent.click(screen.getByRole('button', { name: 'Jetzt abgleichen' }))
    await expectToast(
      'error',
      'Rollen-Sync, Nicknames und Statusrollen sind alle ausgeschaltet, es gibt nichts abzugleichen.',
    )
  })

  it('unlinks only after confirming', async () => {
    let linked = true
    const fetch = stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/members/1': () => json(linked ? detail : { ...detail, link: null }),
      'GET /web/admin/members/1/roles': () => json(linked ? roles : { linked: false, roles: [] }),
      'DELETE /web/admin/members/1/link': () => {
        linked = false
        return status(204)
      },
    })
    renderApp('/admin/members?id=1')

    await userEvent.click(await screen.findByRole('button', { name: 'Verknüpfung lösen' }))
    const dialog = await screen.findByRole('alertdialog', {
      name: 'Verknüpfung lösen?',
    })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
    expect(fetch).not.toHaveBeenCalledWith('/web/admin/members/1/link', expect.anything())

    await userEvent.click(screen.getByRole('button', { name: 'Verknüpfung lösen' }))
    const confirm = await screen.findByRole('alertdialog', {
      name: 'Verknüpfung lösen?',
    })
    expect(confirm).toHaveTextContent('nicht mehr mit Luftiger verknüpft')
    await userEvent.click(within(confirm).getByRole('button', { name: 'Verknüpfung lösen' }))

    await expectToast('success', 'Verknüpfung gelöst')
    expect(await screen.findByText('nicht verknüpft')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Verknüpfung lösen' })).not.toBeInTheDocument()
  })

  it('says so for an unknown member and when WarEra cannot be reached', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/members/77': () => status(404),
      'GET /web/admin/members/1': () => json(detail),
      'GET /web/admin/members/1/roles': () => status(503),
    })
    const router = renderApp('/admin/members?id=77')

    expect(await screen.findByText('Dieses Mitglied ist weder auf dem Server noch verknüpft.')).toBeInTheDocument()

    await router.navigate('/admin/members?id=1')
    expect(
      await screen.findByText(/WarEra ist gerade nicht erreichbar, die Rollen können nicht geprüft werden/),
    ).toBeInTheDocument()
    expect(screen.getByText('Luftiger')).toBeInTheDocument()
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/members/1': () => json(detail),
      'GET /web/admin/members/1/roles': () => json(roles),
    })
    renderApp('/admin/members?id=1')
    await screen.findByText('Bürger Deutschland')

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, {
      rules: { 'color-contrast': { enabled: false } },
    })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
