import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Reprimands } from '@/features/reprimands/api'
import type { Me } from '@/lib/api'
import { expectNoAxeViolations } from '@/test/axe'
import { features, json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const reprimands: Reprimands = {
  reprimands: [
    {
      id: 2,
      discordUserId: '100',
      memberName: 'Spammer',
      reason: 'Werbung im Allgemein-Kanal',
      actorName: 'Mod',
      createdAt: Date.UTC(2026, 2, 2, 12),
      withdrawnAt: null,
      withdrawnBy: null,
    },
    {
      id: 1,
      discordUserId: '101',
      memberName: 'Troll',
      reason: 'Beleidigung',
      actorName: 'Mod',
      createdAt: Date.UTC(2026, 2, 1, 12),
      withdrawnAt: Date.UTC(2026, 2, 1, 13),
      withdrawnBy: 'Luca',
    },
  ],
  counts: [{ discordUserId: '100', memberName: 'Spammer', count: 1 }],
}

function stub(routes: Record<string, (init?: RequestInit) => Response> = {}, data: Reprimands = reprimands) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/reprimands': () => json(data),
    ...routes,
  })
}

describe('reprimands page', () => {
  it('shows the count per member and every reprimand, withdrawn ones marked', async () => {
    stub()
    renderApp('/admin/reprimands')

    const counts = (await screen.findByRole('heading', { name: 'Anzahl pro Mitglied' })).closest('[data-slot=card]') as HTMLElement
    expect(within(counts).getByRole('row', { name: 'Spammer 100 1' })).toBeInTheDocument()
    const withdrawn = screen.getByRole('region', { name: /^Rüge für Troll/ })
    expect(within(withdrawn).getByText('Zurückgenommen')).toBeInTheDocument()
    expect(within(withdrawn).getByText(/zurückgenommen von Luca/)).toBeInTheDocument()
    expect(within(withdrawn).queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText('Werbung im Allgemein-Kanal')).toBeInTheDocument()
  })

  it('withdraws a reprimand after asking', async () => {
    const fetch = stub({ 'POST /web/admin/reprimands/2/withdraw': () => status(204) })
    renderApp('/admin/reprimands')

    await userEvent.click(await screen.findByRole('button', { name: 'Rüge für Spammer zurücknehmen' }))
    await expectNoAxeViolations(document.body, { popup: true })
    await userEvent.click(screen.getByRole('button', { name: 'Zurücknehmen' }))

    await expectToast('success', 'Rüge zurückgenommen')
    expect(fetch).toHaveBeenCalledWith('/web/admin/reprimands/2/withdraw', expect.objectContaining({ method: 'POST' }))
  })

  it('names the way to give one while there is none, and says when the feature is off', async () => {
    stub({ 'GET /web/features': () => features('REPRIMANDS') }, { reprimands: [], counts: [] })
    renderApp('/admin/reprimands')

    expect(await screen.findByText('Noch keine Rüge')).toBeInTheDocument()
    expect(screen.getByText('Niemand hat eine Rüge')).toBeInTheDocument()
    expect(await screen.findByText('Diese Funktion ist gerade ausgeschaltet.')).toBeInTheDocument()
  })

  it('has no accessibility violations axe can find', async () => {
    stub()
    renderApp('/admin/reprimands')
    await screen.findByText('Werbung im Allgemein-Kanal')
    await expectNoAxeViolations()
  })
})
