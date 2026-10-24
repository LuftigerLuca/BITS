import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import { createQueryClient } from '@/lib/queryClient'
import type { DiscordRole, RoleRule } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { RoleRulesCard } from './RoleRulesCard'

const veteran: DiscordRole = { id: 'r-veteran', name: 'Veteran', color: null, managed: true }
const citizen: DiscordRole = { id: 'r-citizen', name: 'Bürger', color: 0xff0000, managed: false }

function rule(id: number, role: DiscordRole, label: string): RoleRule {
  return { id, roleId: role.id, roleName: role.name, roleColor: role.color, label, status: 'ACTIVE' }
}

function renderCard(rules: RoleRule[] = [], onCreate: (body: unknown) => Response = () => json(rules[0], 201)) {
  const fetch = stubFetch({
    'GET /web/admin/role-sync/roles': () => json([citizen, veteran]),
    'GET /web/admin/role-sync/rules': () => json(rules),
    'GET /web/admin/role-sync/countries': () => json([{ id: 'de', name: 'Germany' }]),
    'POST /web/admin/role-sync/rules': (init) => onCreate(JSON.parse(String(init?.body))),
    'DELETE /web/admin/role-sync/rules/1': () => status(204),
  })
  const queryClient = createQueryClient()
  queryClient.setDefaultOptions({ queries: { retry: false } })
  const { container } = render(
    <QueryClientProvider client={queryClient}>
      <RoleRulesCard />
    </QueryClientProvider>,
  )
  return { fetch, container }
}

async function choose(label: string, option: string) {
  await userEvent.click(screen.getByLabelText(label))
  await userEvent.click(await screen.findByRole('option', { name: option }))
}

const submit = () => screen.getByRole('button', { name: 'Regel anlegen' })

describe('rule form', () => {
  it('asks for a threshold of at least 1 for level rules', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(9, veteran, 'Level ab 5'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Bedingung', 'Level')
    const threshold = screen.getByLabelText('Level ab')
    await userEvent.clear(threshold)
    await userEvent.type(threshold, '0')
    expect(submit()).toBeDisabled()

    await userEvent.clear(threshold)
    await userEvent.type(threshold, '5')
    await userEvent.click(submit())

    // Veteran is managed already, so no confirmation is needed.
    expect(screen.queryByText('Rolle an BITS übergeben?')).not.toBeInTheDocument()
    await expect.poll(() => created).toEqual([{ roleId: 'r-veteran', type: 'LEVEL', threshold: 5 }])
  })

  it('needs a country for citizenship rules and confirms the first rule of a role', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(9, citizen, 'Staatsbürgerschaft Germany'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Bürger')
    expect(submit()).toBeDisabled()
    await choose('Land', 'Germany')
    await userEvent.click(submit())

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('Rolle an BITS übergeben?')).toBeInTheDocument()
    expect(created).toEqual([])
    await userEvent.click(within(dialog).getByRole('button', { name: 'Regel anlegen' }))
    await expect.poll(() => created).toEqual([{ roleId: 'r-citizen', type: 'CITIZENSHIP', countryId: 'de' }])
  })

  it('explains a duplicate rule', async () => {
    renderCard([], () => status(409))
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Bedingung', 'Level')
    await userEvent.click(submit())

    expect(await screen.findByText('Diese Regel gibt es für die Rolle schon.')).toBeInTheDocument()
  })

  it('names every field', async () => {
    const { container } = renderCard()
    await screen.findByLabelText('Rolle')
    await choose('Bedingung', 'Regierungsamt')

    const results = await axe.run(container, { runOnly: ['label', 'button-name', 'select-name', 'aria-input-field-name'] })

    expect(results.violations).toEqual([])
    expect(screen.getByLabelText('Amt')).toBeInTheDocument()
  })
})

describe('rule table', () => {
  it('shows a loading state, not the empty state, while the rules load', async () => {
    renderCard()

    expect(screen.getByText('Regeln werden geladen …')).toBeInTheDocument()
    expect(screen.queryByText('Noch keine Regeln angelegt.')).not.toBeInTheDocument()
    expect(await screen.findByText('Noch keine Regeln angelegt.')).toBeInTheDocument()
  })

  it('shows an error with retry when the rules cannot be loaded', async () => {
    stubFetch({
      'GET /web/admin/role-sync/roles': () => json([]),
      'GET /web/admin/role-sync/rules': () => status(500),
    })
    const queryClient = createQueryClient()
    queryClient.setDefaultOptions({ queries: { retry: false } })
    render(
      <QueryClientProvider client={queryClient}>
        <RoleRulesCard />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('button', { name: 'Erneut versuchen' })).toBeInTheDocument()
    expect(screen.queryByText('Noch keine Regeln angelegt.')).not.toBeInTheDocument()
  })

  it('deletes a rule only after confirmation, and says when it is the last of its role', async () => {
    const { fetch } = renderCard([rule(1, veteran, 'Level ab 10')])

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ löschen' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/letzte Regel der Rolle/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining('/rules/1'), expect.anything())

    await userEvent.click(screen.getByRole('button', { name: 'Regel „Level ab 10“ löschen' }))
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Regel löschen' }))
    await expect
      .poll(() => fetch.mock.calls.some(([url, init]) => String(url).endsWith('/rules/1') && init?.method === 'DELETE'))
      .toBe(true)
  })

  it('warns that members lose the role when other rules of the role remain', async () => {
    renderCard([rule(1, veteran, 'Level ab 10'), rule(2, veteran, 'Militärrang ab 20')])

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ löschen' }))

    expect(within(await screen.findByRole('alertdialog')).getByText(/verlieren die Rolle sofort/)).toBeInTheDocument()
  })
})
