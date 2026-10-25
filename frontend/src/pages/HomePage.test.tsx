import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import type { Me, Overview } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null }

const overview: Overview = {
  members: 1234,
  verifiedMembers: 617,
  country: { id: 'de', name: 'Germany', available: true, citizens: 982, activeCitizens: 925 },
}

/** The text of each tile: what it counts, the number and the note below. */
async function tiles() {
  const section = await screen.findByRole('region', { name: 'Kennzahlen' })
  return [...section.querySelectorAll('[data-slot=card]')].map((tile) => tile.textContent)
}

describe('home page', () => {
  it('shows admins the members, how many are verified and the citizens of the country', async () => {
    stubFetch({ 'GET /web/me': () => json(admin), 'GET /web/admin/overview': () => json(overview) })

    renderApp('/')

    expect(await tiles()).toEqual([
      'Discord-Mitglieder1.234ohne Bots',
      'davon verifiziert61750 % mit WarEra-Account',
      'Bürger von Germany982davon aktiv: 925',
    ])
  })

  it('marks each tile with a decorative icon, also when a figure is missing', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    })

    renderApp('/')

    const section = await screen.findByRole('region', { name: 'Kennzahlen' })
    const icons = [...section.querySelectorAll('[data-slot=card] svg')]
    expect(icons.map((icon) => icon.getAttribute('class'))).toEqual([
      expect.stringContaining('lucide-users'),
      expect.stringContaining('lucide-badge-check'),
      expect.stringContaining('lucide-flag'),
    ])
    expect(icons.map((icon) => icon.getAttribute('aria-hidden'))).toEqual(['true', 'true', 'true'])
  })

  it('shows members no figures and does not ask for them', async () => {
    const fetch = stubFetch({
      'GET /web/me': () => json({ ...admin, role: 'USER' }),
      'GET /web/votes/access': () => json({ canManage: false, canConfigure: false }),
    })

    renderApp('/')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Kennzahlen' })).not.toBeInTheDocument()
    // The sidebar asks whether to show the votes, nobody asks for the figures.
    expect(fetch.mock.calls.map(([input]) => String(input))).not.toContainEqual(expect.stringContaining('overview'))
  })

  it('says the members are unknown until the bot reported them, and links to the country setting', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
    })

    const router = renderApp('/')

    const [members, verified, citizens] = await tiles()
    expect(members).toBe('Discord-MitgliederunbekanntDer Bot hat die Mitglieder noch nicht gemeldet.')
    expect(verified).toBe('davon verifiziertunbekanntDer Bot hat die Mitglieder noch nicht gemeldet.')
    expect(citizens).toBe('Bürgerkein LandNoch kein Land eingestellt. In der Verwaltung einstellen')
    await userEvent.click(screen.getByRole('link', { name: 'In der Verwaltung einstellen' }))
    expect(await screen.findByRole('heading', { name: 'Verwaltung' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('keeps the Discord figures when WarEra cannot be reached', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () =>
        json({ ...overview, country: { id: 'de', name: 'Germany', available: false, citizens: null, activeCitizens: null } }),
    })

    renderApp('/')

    expect(await tiles()).toEqual([
      'Discord-Mitglieder1.234ohne Bots',
      'davon verifiziert61750 % mit WarEra-Account',
      'Bürger von Germanynicht verfügbarWarEra ist gerade nicht erreichbar.',
    ])
  })

  it('offers to try again if the figures cannot be loaded', async () => {
    stubFetch({ 'GET /web/me': () => json(admin), 'GET /web/admin/overview': () => status(500) })

    renderApp('/')

    expect(await screen.findByText(/Die Kennzahlen konnten nicht geladen werden/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeInTheDocument()
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stubFetch({ 'GET /web/me': () => json(admin), 'GET /web/admin/overview': () => json(overview) })
    renderApp('/')
    await tiles()

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
