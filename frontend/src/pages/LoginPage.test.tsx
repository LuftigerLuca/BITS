import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

function stubLogin(loginStatus: number) {
  return stubFetch({
    'GET /web/me': () => status(401),
    'GET /auth/options': () => json({ adminLogin: true }),
    'POST /auth/login': () => status(loginStatus),
  })
}

async function loginAsAdmin() {
  await userEvent.type(await screen.findByLabelText('Benutzername'), 'admin')
  await userEvent.type(screen.getByLabelText('Passwort'), 'wrong')
  await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }))
}

describe('login page', () => {
  it('explains the error code of a failed Discord login', async () => {
    stubFetch({ 'GET /web/me': () => status(401), 'GET /auth/options': () => json({ adminLogin: false }) })

    renderApp('/login?error=not_member')

    expect(await screen.findByText('Kein Mitglied des Discord-Servers.')).toBeInTheDocument()
  })

  it('has a fallback for unknown error codes', async () => {
    stubFetch({ 'GET /web/me': () => status(401), 'GET /auth/options': () => json({ adminLogin: false }) })

    renderApp('/login?error=something_new')

    expect(await screen.findByText('Die Anmeldung mit Discord ist fehlgeschlagen.')).toBeInTheDocument()
  })

  it('shows the admin login only when it is enabled', async () => {
    stubFetch({ 'GET /web/me': () => status(401), 'GET /auth/options': () => json({ adminLogin: false }) })

    renderApp('/login')

    await screen.findByRole('button', { name: 'Mit Discord anmelden' })
    expect(screen.queryByLabelText('Benutzername')).not.toBeInTheDocument()
  })

  it('labels the Discord button by its text, not by the logo', async () => {
    stubFetch({ 'GET /web/me': () => status(401), 'GET /auth/options': () => json({ adminLogin: false }) })

    renderApp('/login')

    const button = await screen.findByRole('button', { name: 'Mit Discord anmelden' })
    expect(button.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('reports wrong credentials', async () => {
    stubLogin(401)
    renderApp('/login')

    await loginAsAdmin()

    expect(await screen.findByText('Benutzername oder Passwort falsch.')).toBeInTheDocument()
  })

  it('reports the lockout', async () => {
    stubLogin(429)
    renderApp('/login')

    await loginAsAdmin()

    expect(await screen.findByText(/Admin-Anmeldung ist vorübergehend gesperrt/)).toBeInTheDocument()
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stubLogin(401)
    renderApp('/login')
    await screen.findByLabelText('Benutzername')

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
