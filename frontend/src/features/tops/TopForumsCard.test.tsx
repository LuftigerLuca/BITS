import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import type { TopSettings } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const settings: TopSettings = {
  forums: [
    {
      id: 'f-1',
      name: 'tops',
      category: 'Kongress',
      usable: true,
      tags: [
        { id: 't-1', name: 'Erledigt' },
        { id: 't-2', name: 'Vertagt' },
      ],
    },
    { id: 'f-2', name: 'gesperrt', category: null, usable: false, tags: [] },
  ],
  selected: [],
}

function stub(data: TopSettings, routes: Record<string, (init?: RequestInit) => Response> = {}) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/tops': () => json(data),
    ...routes,
  })
}

/** The admin page loads many cards at once, which can take longer than the default second under load. */
const LOADED = { timeout: 5000 }

function card() {
  return screen.getByText('TOP-Foren', { selector: '[data-slot=card-title]' }).closest('[data-slot=card]') as HTMLElement
}

describe('TOP forums', () => {
  it('picks a forum with a done tag and offers none the bot cannot manage', async () => {
    const saved: unknown[] = []
    stub(settings, {
      'PUT /web/admin/tops': (init) => {
        saved.push(JSON.parse(String(init?.body)))
        return status(204)
      },
    })
    renderApp('/admin')

    await userEvent.click(await screen.findByRole('checkbox', { name: '#tops (Kongress)' }, LOADED))
    expect(within(card()).queryByText(/gesperrt/)).not.toBeInTheDocument()
    await userEvent.click(within(card()).getByLabelText('Erledigt-Tag in #tops'))
    await userEvent.click(await screen.findByRole('option', { name: 'Vertagt' }))
    await expectNoAxeViolations(document.body, { popup: true })
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ forums: [{ forumId: 'f-1', doneTagId: 't-2' }] }])
    await expectToast('success', 'TOP-Foren gespeichert')
  })

  it('keeps a picked forum the bot lost its permissions in, so it can be removed', async () => {
    const saved: unknown[] = []
    stub(
      { ...settings, selected: [{ forumId: 'f-2', doneTagId: null }] },
      {
        'PUT /web/admin/tops': (init) => {
          saved.push(JSON.parse(String(init?.body)))
          return status(204)
        },
      },
    )
    renderApp('/admin')

    const forum = await screen.findByRole('checkbox', { name: '#gesperrt – Bot fehlen Rechte' }, LOADED)
    expect(within(card()).getByRole('button', { name: 'Speichern' })).toBeDisabled()
    await userEvent.click(forum)
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ forums: [] }])
  })

  it('says how to get a forum when there is none', async () => {
    stub({ forums: [], selected: [] })
    renderApp('/admin')

    expect(await screen.findByText('Keine Foren verfügbar', {}, LOADED)).toBeInTheDocument()
    await expectNoAxeViolations()
  })

  it('explains a forum the bot may no longer manage', async () => {
    stub(settings, { 'PUT /web/admin/tops': () => status(422) })
    renderApp('/admin')

    await userEvent.click(await screen.findByRole('checkbox', { name: '#tops (Kongress)' }, LOADED))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expectToast('error', 'Dem Bot fehlen in einem der Foren Rechte.')
  })
})
