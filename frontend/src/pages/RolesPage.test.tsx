import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectNoAxeViolations } from '@/test/axe'
import type { DiscordRole, RoleRule } from '@/features/role-sync/api'
import type { Me } from '@/lib/api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

describe('roles page', () => {
  it('is linked from the sidebar and shows the role rules', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/role-sync/roles': () => json([]),
      'GET /web/admin/role-sync/rules': () => json([]),
      'GET /web/admin/role-sync/status-roles': () => json({ verified: null, unverified: null }),
      'GET /web/admin/role-sync/removal-limit': () => json({ enabled: true }),
    })
    const router = renderApp('/admin')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(within(navigation).getByRole('link', { name: 'Rollen' }))

    expect(await screen.findByRole('heading', { name: 'Rollen' })).toBeInTheDocument()
    expect(await screen.findByText('Noch keine Regeln angelegt')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/roles')
  })

  it('keeps members out', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, role: 'USER' }) })

    const router = renderApp('/admin/roles')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('has no accessibility violations axe can find, also with a rule being written and a dialog open', async () => {
    const role: DiscordRole = {
      id: 'r-veteran',
      name: 'Veteran',
      color: null,
      managed: true,
      dskName: null,
      statusRole: false,
    }
    const rule: RoleRule = {
      roleId: role.id,
      roleName: role.name,
      roleColor: null,
      label: 'Level ab 10',
      blocks: [{ label: 'Level ab 10', conditions: [] }],
      status: 'ACTIVE',
    }
    const preview = {
      roleId: role.id,
      roleName: role.name,
      managed: true,
      membersReported: true,
      usersLoaded: true,
      gains: [],
      losses: [],
      unknown: [],
    }
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/role-sync/countries': () => json([{ id: 'de', name: 'Germany' }]),
      'GET /web/admin/role-sync/roles': () => json([role]),
      'GET /web/admin/role-sync/rules': () => json([rule]),
      'GET /web/admin/role-sync/status-roles': () => json({ verified: null, unverified: null }),
      'GET /web/admin/role-sync/removal-limit': () => json({ enabled: true }),
      'GET /web/admin/role-sync/rules/r-veteran/deletion-preview': () => json(preview),
    })
    renderApp('/admin/roles')
    await screen.findByText('Level ab 10')
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    await expectNoAxeViolations()

    await userEvent.click(screen.getByRole('button', { name: 'Regel „Level ab 10“ löschen' }))
    await screen.findByRole('alertdialog')
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
