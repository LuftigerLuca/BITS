import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import type { Honeypot } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const honeypot: Honeypot = {
  channelId: null,
  kicksPaused: false,
  channels: [
    { id: 'c-1', name: 'falle', category: 'Sonstiges', usable: true },
    { id: 'c-2', name: 'gesperrt', category: null, usable: false },
  ],
}

function stub(data: Honeypot, routes: Record<string, (init?: RequestInit) => Response> = {}) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/tops': () => json({ forums: [], selected: [] }),
    'GET /web/admin/honeypot': () => json(data),
    ...routes,
  })
}

function card() {
  return screen.getByText('Honeypot', { selector: '[data-slot=card-title]' }).closest('[data-slot=card]') as HTMLElement
}

async function open() {
  await screen.findByLabelText('Honeypot-Kanal')
  await userEvent.click(within(card()).getByLabelText('Honeypot-Kanal'))
}

describe('honeypot', () => {
  it('sets a channel only after asking and offers none the bot cannot use', async () => {
    const saved: unknown[] = []
    stub(honeypot, {
      'PUT /web/admin/honeypot': (init) => {
        saved.push(JSON.parse(String(init?.body)))
        return status(204)
      },
    })
    renderApp('/admin')

    await open()
    expect(screen.queryByRole('option', { name: /gesperrt/ })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('option', { name: '#falle (Sonstiges)' }))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('#falle als Honeypot festlegen?')).toBeInTheDocument()
    expect(saved).toEqual([])
    await userEvent.click(within(dialog).getByRole('button', { name: 'Als Honeypot festlegen' }))

    await expect.poll(() => saved).toEqual([{ channelId: 'c-1' }])
    await expectToast('success', 'Honeypot festgelegt')
  })

  it('removes the honeypot without asking', async () => {
    const saved: unknown[] = []
    stub(
      { ...honeypot, channelId: 'c-1' },
      {
        'PUT /web/admin/honeypot': (init) => {
          saved.push(JSON.parse(String(init?.body)))
          return status(204)
        },
      },
    )
    renderApp('/admin')

    await open()
    await userEvent.click(await screen.findByRole('option', { name: 'Kein Honeypot' }))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => saved).toEqual([{ channelId: null }])
    await expectToast('success', 'Honeypot entfernt')
  })

  it('shows a pause of the kicks and lifts it', async () => {
    let resumed = false
    stub(
      { ...honeypot, channelId: 'c-1', kicksPaused: true },
      {
        'POST /web/admin/honeypot/resume': () => {
          resumed = true
          return status(204)
        },
      },
    )
    renderApp('/admin')

    await screen.findByText('Kicks pausiert')
    await userEvent.click(within(card()).getByRole('button', { name: 'Kicks fortsetzen' }))

    await expect.poll(() => resumed).toBe(true)
    await expectToast('success', 'Kicks fortgesetzt')
  })

  it('says when the bot lacks the permissions', async () => {
    stub(honeypot, { 'PUT /web/admin/honeypot': () => status(422) })
    renderApp('/admin')

    await open()
    await userEvent.click(await screen.findByRole('option', { name: '#falle (Sonstiges)' }))
    await userEvent.click(within(card()).getByRole('button', { name: 'Speichern' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Als Honeypot festlegen' }))

    await expectToast('error', 'Dem Bot fehlen in diesem Kanal Rechte, oder ihm fehlt „Mitglieder kicken“.')
  })

  it('has no accessibility violations axe can find', async () => {
    stub({ ...honeypot, channelId: 'c-1', kicksPaused: true })
    renderApp('/admin')
    await screen.findByText('Kicks pausiert')
    await expectNoAxeViolations()
  })
})
