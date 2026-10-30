import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { MANUAL_PAGES } from '@/features/manual/manual'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectNoAxeViolations } from '@/test/axe'

const member: Me = { name: 'Luca', avatarUrl: null, role: 'USER', wareraUsername: null, discordUser: true }

describe('manual', () => {
  it('opens without a login and leads to each page', async () => {
    stubFetch({ 'GET /web/me': () => status(401) })

    const router = renderApp('/docs')

    expect(await screen.findByRole('heading', { level: 1, name: 'Handbuch' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Anmelden' })).toHaveAttribute('href', '/login')
    await userEvent.click(screen.getByRole('link', { name: /Abstimmungen leiten/ }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Abstimmungen leiten' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/docs/abstimmungen')
  })

  it('lists the topics of a page and links to the pages it builds on', async () => {
    stubFetch({ 'GET /web/me': () => status(401) })

    renderApp('/docs/admins')

    await screen.findByRole('heading', { level: 1, name: 'Admins' })
    const contents = screen.getByRole('navigation', { name: 'Auf dieser Seite' })
    expect(within(contents).getByRole('link', { name: 'Botschafts-Threads' })).toHaveAttribute(
      'href',
      '#embassies',
    )
    expect(screen.getByRole('heading', { level: 2, name: 'Botschafts-Threads' }).closest('section')).toHaveAttribute(
      'id',
      'embassies',
    )
    expect(screen.getByRole('link', { name: 'Mitglieder' })).toHaveAttribute('href', '/docs/mitglieder')
  })

  it('sends unknown pages to the overview', async () => {
    stubFetch({ 'GET /web/me': () => status(401) })

    const router = renderApp('/docs/gibt-es-nicht')

    expect(await screen.findByRole('heading', { level: 1, name: 'Handbuch' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/docs')
  })

  it('leads logged-in members back to the dashboard', async () => {
    stubFetch({ 'GET /web/me': () => json(member) })

    renderApp('/docs/mitglieder')

    expect(await screen.findByRole('link', { name: 'Zum Dashboard' })).toHaveAttribute('href', '/')
  })

  it('has unique topic ids within each page', () => {
    for (const page of MANUAL_PAGES) {
      const ids = page.topics.map((topic) => topic.id)
      expect(new Set(ids).size).toBe(ids.length)
    }
  })

  it('has no accessibility violations', async () => {
    stubFetch({ 'GET /web/me': () => status(401) })

    renderApp('/docs/mitglieder')
    await screen.findByRole('heading', { level: 1, name: 'Mitglieder' })

    await expectNoAxeViolations()
  })
})
