import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import type { DiscordRole, Feature, StatusRoles } from '@/lib/api'
import { createQueryClient } from '@/lib/queryClient'
import { features, json, status, stubFetch } from '@/test/fetch'
import { expectToast } from '@/test/toast'
import { StatusRolesCard } from './StatusRolesCard'

function role(id: string, name: string, fields: Partial<DiscordRole> = {}): DiscordRole {
  return {
    id,
    name,
    color: null,
    managed: false,
    dskName: null,
    statusRole: false,
    ...fields,
  }
}

const roles = [
  role('r-verified', 'Verified', { statusRole: true }),
  role('r-unverified', 'Unverified'),
  role('r-veteran', 'Veteran', { managed: true }),
  role('r-dsk', 'DSK1', { dskName: 'DSK1' }),
]

function renderCard(
  saved: StatusRoles = { verified: null, unverified: null },
  onSave: (body: unknown) => Response = () => status(204),
  off: Feature[] = [],
) {
  const fetch = stubFetch({
    'GET /web/features': () => features(...off),
    'GET /web/admin/role-sync/roles': () => json(roles),
    'GET /web/admin/role-sync/status-roles': () => json(saved),
    'PUT /web/admin/role-sync/status-roles': (init) => onSave(JSON.parse(String(init?.body))),
  })
  const queryClient = createQueryClient()
  queryClient.setDefaultOptions({ queries: { retry: false } })
  render(
    <QueryClientProvider client={queryClient}>
      <StatusRolesCard />
      <Toaster />
    </QueryClientProvider>,
  )
  return fetch
}

async function choose(label: string, option: string) {
  await userEvent.click(await screen.findByLabelText(label))
  await userEvent.click(await screen.findByRole('option', { name: option }))
}

describe('status roles', () => {
  it('saves both roles, offering neither roles with a rule or of a DSK nor the other status role', async () => {
    let body: unknown
    renderCard({ verified: null, unverified: null }, (b) => {
      body = b
      return status(204)
    })
    const save = await screen.findByRole('button', { name: 'Speichern' })
    expect(save).toBeDisabled()

    await choose('Rolle für Verifizierte', 'Verified')
    await userEvent.click(screen.getByLabelText('Rolle für Unverifizierte'))
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent)
    expect(options).toEqual(['Keine', 'Unverified'])
    await userEvent.click(screen.getByRole('option', { name: 'Unverified' }))
    await userEvent.click(save)

    await expectToast('success', 'Statusrollen gespeichert')
    expect(body).toEqual({
      verifiedRoleId: 'r-verified',
      unverifiedRoleId: 'r-unverified',
    })
  })

  it('warns that a replaced role stays with the members', async () => {
    let body: unknown
    renderCard(
      {
        verified: {
          roleId: 'r-verified',
          roleName: 'Verified',
          roleColor: null,
          status: 'ACTIVE',
        },
        unverified: null,
      },
      (b) => {
        body = b
        return status(204)
      },
    )

    await choose('Rolle für Verifizierte', 'Keine')

    expect(screen.getByText(/BITS entfernt @Verified nicht/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await expectToast('success', 'Statusrollen gespeichert')
    expect(body).toEqual({ verifiedRoleId: null, unverifiedRoleId: null })
  })

  it('marks a deleted role and says when the verification is off', async () => {
    renderCard(
      {
        verified: {
          roleId: 'r-gone',
          roleName: null,
          roleColor: null,
          status: 'ORPHANED',
        },
        unverified: null,
      },
      () => status(204),
      ['VERIFICATION'],
    )

    expect(await screen.findByText('Verwaist')).toBeInTheDocument()
    expect(
      screen.getByText('Gelöschte Rolle', {
        selector: '[data-slot=searchable-select-value]',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText(/BITS ändert an den Statusrollen nichts/)).toBeInTheDocument()
  })

  it('tells why saving failed', async () => {
    renderCard({ verified: null, unverified: null }, () => status(409))

    await choose('Rolle für Verifizierte', 'Verified')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await expectToast(
      'error',
      'Für diese Rolle gibt es schon eine Regel, oder sie gehört zu einer DSK oder ist eine Statusrolle.',
    )
  })
})
