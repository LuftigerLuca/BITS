import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { afterEach, describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

afterEach(() => {
  localStorage.clear()
  document.documentElement.classList.remove('dark')
  window.innerWidth = 1024
})

async function openUserMenu() {
  await userEvent.click(await screen.findByRole('button', { name: /Luca/ }))
}

describe('layout', () => {
  it('marks the page that is open in the sidebar', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
    })

    renderApp('/admin')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).getByRole('link', { name: 'Verwaltung' })).toHaveAttribute('aria-current', 'page')
    expect(within(navigation).getByRole('link', { name: 'Start' })).not.toHaveAttribute('aria-current')
  })

  it('marks only the roles page when it is open, not the admin area above it', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/role-sync/roles': () => json([]),
      'GET /web/admin/role-sync/rules': () => json([]),
    })

    renderApp('/admin/roles')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    const current = within(navigation)
      .getAllByRole('link')
      .filter((link) => link.getAttribute('aria-current') === 'page')
    expect(current.map((link) => link.textContent)).toEqual(['Rollen'])
  })

  it('marks only the log page that is open, not the admin area above it', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/audit-log': () => json({ entries: [], nextBefore: null }),
      'GET /web/admin/audit-log/actors': () => json([]),
    })

    renderApp('/admin/audit-log')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    const current = within(navigation)
      .getAllByRole('link')
      .filter((link) => link.getAttribute('aria-current') === 'page')
    expect(current.map((link) => link.textContent)).toEqual(['Audit-Log'])
  })

  it('shows admins the admin group with the admin area, the roles, the embassies and both logs', async () => {
    stubFetch({ 'GET /web/me': () => json(admin), 'GET /web/admin/overview': () => json({}) })

    renderApp('/')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).getByText('Admin')).toBeInTheDocument()
    expect(within(navigation).getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Start', '/'],
      ['Mein Account', '/account'],
      ['Verwaltung', '/admin'],
      ['Rollen', '/admin/roles'],
      ['Botschaften', '/admin/embassies'],
      ['Audit-Log', '/admin/audit-log'],
      ['System-Log', '/admin/system-log'],
    ])
  })

  it('shows members neither the admin group nor its pages', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, role: 'USER' }) })

    renderApp('/')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).queryByText('Admin')).not.toBeInTheDocument()
    expect(within(navigation).getAllByRole('link').map((link) => link.textContent)).toEqual(['Start', 'Mein Account'])
  })

  it('shows the admin account without Discord no page of its own account', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, discordUser: false }), 'GET /web/admin/overview': () => json({}) })

    renderApp('/')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    expect(within(navigation).queryByRole('link', { name: 'Mein Account' })).not.toBeInTheDocument()
  })

  it('applies and keeps the theme picked in the user menu', async () => {
    stubFetch({ 'GET /web/me': () => json(admin) })
    renderApp('/')

    await openUserMenu()
    await userEvent.click(await screen.findByRole('menuitemradio', { name: 'Dunkel' }))

    expect(document.documentElement).toHaveClass('dark')
    expect(localStorage.getItem('theme')).toBe('dark')

    // The menu stays open after a theme choice, so the change can be seen right away.
    await userEvent.click(screen.getByRole('menuitemradio', { name: 'System' }))

    // The test setup reports a light system setting.
    expect(document.documentElement).not.toHaveClass('dark')
    expect(localStorage.getItem('theme')).toBeNull()
  })

  it('opens the manual from the user menu', async () => {
    stubFetch({ 'GET /web/me': () => json(admin), 'GET /web/admin/overview': () => json({}) })
    const router = renderApp('/')

    await openUserMenu()
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Handbuch' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Handbuch' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/docs')
  })

  it('logs out from the user menu and goes to the login page', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'POST /auth/logout': () => status(204),
      'GET /auth/options': () => json({ adminLogin: false }),
    })
    const router = renderApp('/')

    await openUserMenu()
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Abmelden' }))

    expect(await screen.findByRole('button', { name: 'Mit Discord anmelden' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('opens the sidebar on small screens with the menu button and closes it after navigating', async () => {
    window.innerWidth = 500
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
    })
    const router = renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'Menü öffnen' }))
    const sidebar = await screen.findByRole('dialog', { name: 'Navigation' })
    await userEvent.click(within(sidebar).getByRole('link', { name: 'Verwaltung' }))

    expect(router.state.location.pathname).toBe('/admin')
    await expect.poll(() => screen.queryByRole('dialog')).toBeNull()
  })

  it('closes the sidebar on small screens after opening a log', async () => {
    window.innerWidth = 500
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({}),
      'GET /web/admin/system-log': () => json({ entries: [], nextBefore: null }),
      'GET /web/admin/system-log/roles': () => json([]),
    })
    const router = renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'Menü öffnen' }))
    const sidebar = await screen.findByRole('dialog', { name: 'Navigation' })
    await userEvent.click(within(sidebar).getByRole('link', { name: 'System-Log' }))

    expect(await screen.findByRole('heading', { name: 'System-Log' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/system-log')
    await expect.poll(() => screen.queryByRole('dialog')).toBeNull()
  })

  it('has no accessibility violations axe can find without a browser on a log page', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/audit-log': () => json({ entries: [], nextBefore: null }),
      'GET /web/admin/audit-log/actors': () => json([]),
    })
    renderApp('/admin/audit-log')
    await screen.findByText('Noch keine Einträge.')

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })

  it('has no accessibility violations axe can find without a browser with the user menu open', async () => {
    stubFetch({ 'GET /web/me': () => json(admin) })
    renderApp('/')

    await openUserMenu()
    await screen.findByRole('menu')
    // jsdom does not lay out the page, so contrast is left to the browser. The open menu is rendered at the end of
    // the body like every popup, outside the landmarks; the guards tests check the landmarks with the menu closed.
    const results = await axe.run(document.body, {
      rules: { 'color-contrast': { enabled: false }, region: { enabled: false } },
    })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
