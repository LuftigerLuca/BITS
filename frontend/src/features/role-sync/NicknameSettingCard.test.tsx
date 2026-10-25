import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null }

function stub(setSetting: (init?: RequestInit) => Response) {
  let enabled = false
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/role-sync/roles': () => json([]),
    'GET /web/admin/role-sync/rules': () => json([]),
    'GET /web/admin/role-sync/countries': () => json([]),
    'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
    'GET /web/admin/role-sync/nicknames': () => json({ enabled }),
    'PUT /web/admin/role-sync/nicknames': (init) => {
      const response = setSetting(init)
      if (response.ok) enabled = JSON.parse(String(init?.body)).enabled
      return response
    },
  })
}

describe('nickname setting', () => {
  it('is off at first and turns on', async () => {
    const saved: boolean[] = []
    stub((init) => {
      saved.push(JSON.parse(String(init?.body)).enabled)
      return json({ enabled: true })
    })
    renderApp('/admin')

    const toggle = await screen.findByRole('switch', { name: 'Nickname auf den WarEra-Namen setzen' })
    expect(toggle).not.toBeChecked()
    await userEvent.click(toggle)

    await vi.waitFor(() => expect(saved).toEqual([true]))
    // Loaded again after saving.
    await vi.waitFor(() => expect(screen.getByRole('switch')).toBeChecked())
  })

  it('says when saving failed', async () => {
    stub(() => status(500))
    renderApp('/admin')

    await userEvent.click(await screen.findByRole('switch'))

    expect(
      await screen.findByText('Die Einstellung konnte nicht gespeichert werden. Bitte versuche es erneut.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('switch')).not.toBeChecked()
  })
})
