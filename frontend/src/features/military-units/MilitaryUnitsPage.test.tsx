import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'
import { expectToast } from '@/test/toast'
import { expectNoAxeViolations } from '@/test/axe'
import type { MilitaryUnits } from './api'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const militaryUnits: MilitaryUnits = {
  roles: [
    { id: 'r-dsk1', name: 'DSK1', color: 0x0000ff, hasRule: false, statusRole: false, dskId: 1 },
    { id: 'r-dsk2', name: 'DSK2', color: null, hasRule: false, statusRole: false, dskId: null },
    { id: 'r-veteran', name: 'Veteran', color: null, hasRule: true, statusRole: false, dskId: null },
  ],
  dsks: [
    {
      id: 1,
      name: 'DSK1',
      roleId: 'r-dsk1',
      roleName: 'DSK1',
      roleColor: 0x0000ff,
      roleStatus: 'ACTIVE',
      militaryUnits: [{ id: 'm1', name: 'Deutsche Marine' }],
    },
  ],
}

function stub(routes: Record<string, (init?: RequestInit) => Response> = {}, data: MilitaryUnits = militaryUnits) {
  return stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/votes/access': () => json({ canManage: false }),
    'GET /web/admin/military-units': () => json(data),
    ...routes,
  })
}

function bodies(calls: unknown[]) {
  return (init?: RequestInit) => {
    calls.push(init?.body ? JSON.parse(String(init.body)) : null)
    return status(204)
  }
}

describe('military units', () => {
  it('shows the DSKs with their role and military units', async () => {
    stub()
    renderApp('/admin/military-units')

    const section = await screen.findByRole('region', { name: 'DSK1' })
    expect(within(section).getByText('Deutsche Marine')).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Deutsche Marine aus DSK1 entfernen' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Militäreinheiten' })).toHaveAttribute('aria-current', 'page')
  })

  it('creates a DSK with a free role only', async () => {
    const created: unknown[] = []
    stub({ 'POST /web/admin/military-units/dsks': bodies(created) })
    renderApp('/admin/military-units')

    await userEvent.type(await screen.findByLabelText('Name'), 'DSK2')
    await userEvent.click(screen.getByLabelText('Rolle'))
    // Roles with a rule and those of another DSK are taken.
    expect(screen.queryByRole('option', { name: 'Veteran' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'DSK1' })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('option', { name: 'DSK2' }))
    await userEvent.click(screen.getByRole('button', { name: 'DSK anlegen' }))

    await expect.poll(() => created).toEqual([{ name: 'DSK2', roleId: 'r-dsk2' }])
    await expectToast('success', 'DSK angelegt')
  })

  it('assigns a military unit of any country found by name, but not one of another DSK', async () => {
    const added: unknown[] = []
    stub({
      'GET /web/admin/military-units/search': () =>
        json([
          { id: 'm2', name: 'Deutsche Luftwaffe', countryName: 'Germany', dskName: null },
          { id: 'm3', name: 'Deutsches Heer', countryName: 'Germany', dskName: 'DSK2' },
          { id: 'm4', name: 'Deutsche Fremdenlegion', countryName: 'France', dskName: null },
        ]),
      'POST /web/admin/military-units/dsks/1/military-units': bodies(added),
    })
    renderApp('/admin/military-units')

    await userEvent.type(await screen.findByLabelText('MU hinzufügen'), 'Deutsch{Enter}')
    expect(await screen.findByRole('button', { name: 'Deutsches Heer zu DSK1 hinzufügen' })).toBeDisabled()
    expect(screen.getByText(/France/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Deutsche Fremdenlegion zu DSK1 hinzufügen' }))

    await expect.poll(() => added).toEqual([{ muId: 'm4' }])
    await expectToast('success', 'MU zugeordnet')
  })

  it('removes a military unit and deletes a DSK after asking', async () => {
    const removed: unknown[] = []
    const deleted: unknown[] = []
    stub({
      'DELETE /web/admin/military-units/dsks/1/military-units/m1': bodies(removed),
      'DELETE /web/admin/military-units/dsks/1': bodies(deleted),
    })
    renderApp('/admin/military-units')

    await userEvent.click(await screen.findByRole('button', { name: 'Deutsche Marine aus DSK1 entfernen' }))
    const unitDialog = await screen.findByRole('alertdialog')
    expect(within(unitDialog).getByText(/entzieht den Mitgliedern der MU die Rolle @DSK1/)).toBeInTheDocument()
    await userEvent.click(within(unitDialog).getByRole('button', { name: 'Entfernen' }))
    await expect.poll(() => removed).toHaveLength(1)

    await userEvent.click(screen.getByRole('button', { name: 'DSK1 löschen' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/entzieht allen die Rolle @DSK1/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Löschen' }))
    await expect.poll(() => deleted).toHaveLength(1)
    await expectToast('success', 'DSK gelöscht')
  })

  it('renames a DSK and takes its role away', async () => {
    const updated: unknown[] = []
    stub({ 'PUT /web/admin/military-units/dsks/1': bodies(updated) })
    renderApp('/admin/military-units')

    await userEvent.click(await screen.findByRole('button', { name: 'DSK1 bearbeiten' }))
    const section = screen.getByRole('region', { name: 'DSK1' })
    const name = within(section).getByLabelText('Name')
    await userEvent.clear(name)
    await userEvent.type(name, 'DSK 1')
    await userEvent.click(within(section).getByLabelText('Rolle'))
    await userEvent.click(await screen.findByRole('option', { name: 'Keine Rolle' }))
    await userEvent.click(within(section).getByRole('button', { name: 'Speichern' }))

    await expect.poll(() => updated).toEqual([{ name: 'DSK 1', roleId: null }])
  })

  it('says why a change failed', async () => {
    stub({ 'POST /web/admin/military-units/dsks': () => status(409) })
    renderApp('/admin/military-units')

    await userEvent.type(await screen.findByLabelText('Name'), 'DSK1')
    await userEvent.click(screen.getByRole('button', { name: 'DSK anlegen' }))

    await expectToast(
      'error',
      'Der Name, die Rolle oder die MU ist schon vergeben, oder die Rolle hat eine Regel unter „Rollen“.',
    )
  })

  it('has no accessibility violations axe can find, also while editing and with a dialog open', async () => {
    stub()
    renderApp('/admin/military-units')
    await screen.findByRole('region', { name: 'DSK1' })
    await expectNoAxeViolations()

    await userEvent.click(screen.getByRole('button', { name: 'DSK1 bearbeiten' }))
    await expectNoAxeViolations()
    await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    await userEvent.click(screen.getByRole('button', { name: 'DSK1 löschen' }))
    await screen.findByRole('alertdialog')
    await expectNoAxeViolations(document.body, { popup: true })
  })
})
