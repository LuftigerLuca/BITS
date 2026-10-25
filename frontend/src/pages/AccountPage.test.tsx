import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import type { Account, AccountRoles, Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const member: Me = { name: 'Luca', avatarUrl: null, role: 'USER', wareraUsername: 'Luftiger', discordUser: true }

// Local times, so the page shows the same in every time zone.
const account: Account = {
  link: { wareraUsername: 'Luftiger', verifiedAt: new Date(2025, 11, 24, 18, 0).getTime() },
  lastSyncAt: new Date(2026, 0, 1, 11, 0).getTime(),
}

const roles: AccountRoles = {
  linked: true,
  roles: [
    {
      id: 'r-citizen',
      name: 'Bürger Deutschland',
      color: 0xff0000,
      outcome: 'MET',
      rules: [{ label: 'Staatsbürgerschaft Germany', met: true, detail: null }],
    },
    {
      id: 'r-veteran',
      name: 'Veteran',
      color: null,
      outcome: 'NOT_MET',
      rules: [{ label: 'Level ab 50', met: false, detail: 'Level 30 von 50' }],
    },
  ],
}

const unlinked: Account = { link: null, lastSyncAt: null }

function rows() {
  return screen
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))
}

describe('account page', () => {
  it('is linked from the sidebar and shows the link, the last sync and the roles with their reasons', async () => {
    stubFetch({
      'GET /web/me': () => json(member),
      'GET /web/account': () => json(account),
      'GET /web/account/roles': () => json(roles),
    })
    const router = renderApp('/')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(within(navigation).getByRole('link', { name: 'Mein Account' }))

    expect(await screen.findByRole('heading', { name: 'Mein Account' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/account')
    const terms = (await screen.findAllByRole('term')).map((term) => [term.textContent, term.nextSibling?.textContent])
    expect(terms).toEqual([
      ['WarEra-Account', 'Luftiger'],
      ['Verknüpft am', '24.12.2025, 18:00'],
      ['Letzter Abgleich', '01.01.2026, 11:00'],
    ])
    await screen.findByText('Bürger Deutschland')
    expect(rows()).toEqual([
      ['Bürger Deutschland', 'Erfüllt', 'Staatsbürgerschaft Germany'],
      ['Veteran', 'Nicht erfüllt', 'Level ab 50 (Level 30 von 50)'],
    ])
    expect(screen.getByRole('link', { name: 'Daten herunterladen' })).toHaveAttribute('href', '/web/account/export')
  })

  it('points members without link to /verify', async () => {
    stubFetch({
      'GET /web/me': () => json({ ...member, wareraUsername: null }),
      'GET /web/account': () => json(unlinked),
      'GET /web/account/roles': () => json({ linked: false, roles: [] }),
    })

    renderApp('/account')

    expect(await screen.findByText(/mit keinem WarEra-Account verknüpft/)).toBeInTheDocument()
    expect(await screen.findByText(/Ohne verknüpften WarEra-Account vergibt BITS dir keine Rollen/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Verknüpfung lösen' })).not.toBeInTheDocument()
  })

  it('unlinks only after confirming, then shows the account without link', async () => {
    let linked = true
    const fetch = stubFetch({
      'GET /web/me': () => json(linked ? member : { ...member, wareraUsername: null }),
      'GET /web/account': () => json(linked ? account : unlinked),
      'GET /web/account/roles': () => json(linked ? roles : { linked: false, roles: [] }),
      'DELETE /web/account/link': () => {
        linked = false
        return status(204)
      },
    })
    renderApp('/account')

    await userEvent.click(await screen.findByRole('button', { name: 'Verknüpfung lösen' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Verknüpfung lösen?' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
    expect(fetch).not.toHaveBeenCalledWith('/web/account/link', expect.anything())

    await userEvent.click(screen.getByRole('button', { name: 'Verknüpfung lösen' }))
    const confirm = await screen.findByRole('alertdialog', { name: 'Verknüpfung lösen?' })
    expect(confirm).toHaveTextContent('nicht mehr mit Luftiger verknüpft')
    await userEvent.click(within(confirm).getByRole('button', { name: 'Verknüpfung lösen' }))

    await expectToast('success', 'Verknüpfung gelöst')
    expect(await screen.findByText(/mit keinem WarEra-Account verknüpft/)).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/web/account/link', expect.objectContaining({ method: 'DELETE' }))
  })

  it('says so if unlinking fails', async () => {
    stubFetch({
      'GET /web/me': () => json(member),
      'GET /web/account': () => json(account),
      'GET /web/account/roles': () => json(roles),
      'DELETE /web/account/link': () => status(500),
    })
    renderApp('/account')

    await userEvent.click(await screen.findByRole('button', { name: 'Verknüpfung lösen' }))
    const dialog = await screen.findByRole('alertdialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Verknüpfung lösen' }))

    await expectToast('error', 'Die Verknüpfung konnte nicht gelöst werden. Bitte versuche es später erneut.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('Luftiger')).toBeInTheDocument()
  })

  it('keeps the link when WarEra cannot be reached for the roles', async () => {
    stubFetch({
      'GET /web/me': () => json(member),
      'GET /web/account': () => json(account),
      'GET /web/account/roles': () => status(503),
    })

    renderApp('/account')

    expect(await screen.findByText(/WarEra ist gerade nicht erreichbar, deine Rollen können nicht geprüft werden/)).toBeInTheDocument()
    expect(screen.getByText('Luftiger')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeInTheDocument()
  })

  it('keeps the admin account without Discord out', async () => {
    stubFetch({
      'GET /web/me': () => json({ ...member, role: 'ADMIN', wareraUsername: null, discordUser: false }),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
    })

    const router = renderApp('/account')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stubFetch({
      'GET /web/me': () => json(member),
      'GET /web/account': () => json(account),
      'GET /web/account/roles': () => json(roles),
    })
    renderApp('/account')
    await screen.findByText('Bürger Deutschland')

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
