import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import type { VerifyPanel } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const panel: VerifyPanel = {
  channelId: null,
  channels: [
    { id: 'c-1', name: 'start', category: 'Willkommen', usable: true },
    { id: 'c-2', name: 'archiv', category: null, usable: false },
  ],
}

function stub(data: VerifyPanel, routes: Record<string, (init?: RequestInit) => Response> = {}) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/tops/manager-roles': () => json({ roles: [], roleIds: [] }),
    'GET /web/admin/tops': () => json({ forums: [], selected: [] }),
    'GET /web/admin/honeypot': () => json({ channelId: null, kicksPaused: false, channels: [] }),
    'GET /web/admin/verify-panel': () => json(data),
    ...routes,
  })
}

function card() {
  return screen.getByText('Verifizierungs-Kanal', { selector: '[data-slot=card-title]' }).closest('[data-slot=card]') as HTMLElement
}

async function open() {
  await screen.findByLabelText('Kanal für neue Mitglieder')
  await userEvent.click(within(card()).getByLabelText('Kanal für neue Mitglieder'))
}

function saving(saved: unknown[]) {
  return {
    'PUT /web/admin/verify-panel': (init?: RequestInit) => {
      saved.push(JSON.parse(String(init?.body)))
      return status(204)
    },
  }
}

describe('verify panel', () => {
  it('sets a channel and offers none the bot cannot post in', async () => {
    const saved: unknown[] = []
    stub(panel, saving(saved))
    renderApp('/admin?tab=server')

    await open()
    expect(screen.queryByRole('option', { name: /archiv/ })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('option', { name: '#start (Willkommen)' }))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ channelId: 'c-1' }])
    await expectToast('success', 'Verifizierungs-Kanal festgelegt')
  })

  it('removes the channel', async () => {
    const saved: unknown[] = []
    stub({ ...panel, channelId: 'c-1' }, saving(saved))
    renderApp('/admin?tab=server')

    await open()
    await userEvent.click(await screen.findByRole('option', { name: 'Kein Verifizierungs-Kanal' }))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ channelId: null }])
    await expectToast('success', 'Verifizierungs-Kanal entfernt')
  })

  it('points out a saved channel the bot can no longer post in', async () => {
    stub({ ...panel, channelId: 'c-2' })
    renderApp('/admin?tab=server')

    expect(await screen.findByText('Dem Bot fehlen in #archiv Rechte. Er kann seine Nachricht dort nicht aktuell halten.')).toBeInTheDocument()
  })

  it('says when the bot may not post in the channel', async () => {
    stub(panel, { 'PUT /web/admin/verify-panel': () => status(422) })
    renderApp('/admin?tab=server')

    await open()
    await userEvent.click(await screen.findByRole('option', { name: '#start (Willkommen)' }))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expectToast('error', 'Der Bot darf in diesem Kanal nicht schreiben.')
  })

  it('has no accessibility violations axe can find', async () => {
    stub({ ...panel, channelId: 'c-2' })
    renderApp('/admin?tab=server')
    await screen.findByText(/Dem Bot fehlen in #archiv Rechte/)
    await expectNoAxeViolations()
  })
})
