import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const member: Me = { name: 'Luca', avatarUrl: null, role: 'USER', wareraUsername: null, discordUser: true }
const admin: Me = { ...member, role: 'ADMIN' }

describe('guards', () => {
  it('send visitors without a session to the login page', async () => {
    stubFetch({ 'GET /web/me': () => status(401), 'GET /auth/options': () => json({ adminLogin: false }) })

    const router = renderApp('/')

    expect(await screen.findByRole('button', { name: 'Mit Discord anmelden' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByText(/Sitzung ist abgelaufen/)).not.toBeInTheDocument()
  })

  it('keep members out of the admin area', async () => {
    stubFetch({ 'GET /web/me': () => json(member) })

    const router = renderApp('/admin')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(screen.queryByRole('link', { name: 'Verwaltung' })).not.toBeInTheDocument()
  })

  it('let admins into the admin area', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/role-sync/roles': () => json([]),
      'GET /web/admin/role-sync/rules': () => json([]),
      'GET /web/admin/role-sync/status-roles': () => json({ verified: null, unverified: null }),
      'GET /web/admin/role-sync/removal-limit': () => json({ enabled: true }),
    })

    renderApp('/admin/roles')

    expect(await screen.findByText('Noch keine Regeln angelegt')).toBeInTheDocument()
  })

  it('show a retry page while BITS does not answer, and go on once it does', async () => {
    let answer = () => status(502)
    stubFetch({ 'GET /web/me': () => answer(), 'GET /auth/options': () => json({ adminLogin: false }) })

    renderApp('/')

    expect(await screen.findByText('Dashboard gerade nicht erreichbar')).toBeInTheDocument()
    answer = () => status(401)
    await userEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }))
    expect(await screen.findByRole('button', { name: 'Mit Discord anmelden' })).toBeInTheDocument()
  })

  it('keep a logged-in user when the check after returning to the tab gets no answer', async () => {
    let answer = () => json(member)
    stubFetch({ 'GET /web/me': () => answer() })
    renderApp('/')
    await screen.findByText('Willkommen, Luca')

    answer = () => {
      throw new TypeError('Failed to fetch')
    }
    await act(async () => document.dispatchEvent(new Event('visibilitychange')))

    expect(screen.getByText('Willkommen, Luca')).toBeInTheDocument()
  })

  it('explain on the login page that the session ran out', async () => {
    let answer = () => json(member)
    stubFetch({ 'GET /web/me': () => answer(), 'GET /auth/options': () => json({ adminLogin: false }) })
    renderApp('/')
    await screen.findByText('Willkommen, Luca')

    answer = () => status(401)
    await act(async () => document.dispatchEvent(new Event('visibilitychange')))

    expect(await screen.findByText('Deine Sitzung ist abgelaufen, bitte melde dich erneut an.')).toBeInTheDocument()
  })

  it('render a start page without accessibility violations axe can find without a browser', async () => {
    stubFetch({ 'GET /web/me': () => json(admin) })
    renderApp('/')
    await screen.findByText('Willkommen, Luca')

    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
