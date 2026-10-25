import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import type { AuditActor, AuditEntry, Me } from '@/lib/api'
import { json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }
const luca: AuditActor = { subject: 'discord:42', name: 'Luca', avatarUrl: null, kind: 'DISCORD' }
const envAdmin: AuditActor = { subject: 'admin:admin', name: 'admin', avatarUrl: null, kind: 'ADMIN_ACCOUNT' }

function entry(id: number, actor: AuditActor, description: string): AuditEntry {
  return { id, occurredAt: Date.UTC(2026, 0, 1, 12, 0), actor, action: 'ROLE_RULE_CREATED', description }
}

/** The query strings the page sent to the audit log; the fetch stub matches by path only. */
function auditLogCalls() {
  const fetch = globalThis.fetch as unknown as { mock: { calls: [RequestInfo | URL][] } }
  return fetch.mock.calls
    .map(([input]) => new URL(input.toString(), 'http://localhost'))
    .filter((url) => url.pathname === '/web/admin/audit-log')
    .map((url) => url.searchParams.toString())
}

describe('audit log page', () => {
  it('shows the newest entries and loads more on request', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/audit-log': () =>
        auditLogCalls().at(-1) === 'before=1'
          ? json({ entries: [entry(0, luca, 'Rolle @Veteran: Level ab 10')], nextBefore: null })
          : json({ entries: [entry(2, envAdmin, 'Rolle @Bürger: Staatsbürgerschaft Germany'), entry(1, luca, 'Rolle @Minister: Level ab 5')], nextBefore: 1 }),
      'GET /web/admin/audit-log/actors': () => json([envAdmin, luca]),
    })

    renderApp('/admin/audit-log')

    expect(await screen.findByText('Rolle @Bürger: Staatsbürgerschaft Germany')).toBeInTheDocument()
    expect(screen.getByText('Admin-Konto')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Mehr laden' }))

    expect(await screen.findByText('Rolle @Veteran: Level ab 10')).toBeInTheDocument()
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[3].textContent)).toEqual([
      'Rolle @Bürger: Staatsbürgerschaft Germany',
      'Rolle @Minister: Level ab 5',
      'Rolle @Veteran: Level ab 10',
    ])
    expect(screen.queryByRole('button', { name: 'Mehr laden' })).not.toBeInTheDocument()
  })

  it('asks the backend for the entries of the chosen admin and action', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/audit-log': () => json({ entries: [], nextBefore: null }),
      'GET /web/admin/audit-log/actors': () => json([envAdmin, luca]),
    })
    renderApp('/admin/audit-log')
    expect(await screen.findByText('Noch keine Einträge.')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('Admin'))
    await userEvent.click(await screen.findByRole('option', { name: 'Luca' }))
    await userEvent.click(screen.getByLabelText('Aktion'))
    await userEvent.click(await screen.findByRole('option', { name: 'Regel gelöscht' }))

    expect(await screen.findByText('Keine Einträge für diesen Filter.')).toBeInTheDocument()
    expect(auditLogCalls().at(-1)).toBe('actor=discord%3A42&action=ROLE_RULE_DELETED')
  })

  it('is linked from the sidebar', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/audit-log': () => json({ entries: [], nextBefore: null }),
      'GET /web/admin/audit-log/actors': () => json([]),
    })
    const router = renderApp('/admin')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(within(navigation).getByRole('link', { name: 'Audit-Log' }))

    expect(await screen.findByRole('heading', { name: 'Audit-Log' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/audit-log')
  })

  it('keeps members out', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, role: 'USER' }) })

    const router = renderApp('/admin/audit-log')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/audit-log': () => json({ entries: [entry(1, luca, 'Rolle @Veteran: Level ab 10')], nextBefore: 1 }),
      'GET /web/admin/audit-log/actors': () => json([luca]),
    })
    renderApp('/admin/audit-log')
    await screen.findByText('Rolle @Veteran: Level ab 10')

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
