import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import { createQueryClient } from '@/lib/queryClient'
import type { DiscordRole, RoleRule, RulePreview } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { RoleRulesCard } from './RoleRulesCard'

const veteran: DiscordRole = { id: 'r-veteran', name: 'Veteran', color: null, managed: true }
const citizen: DiscordRole = { id: 'r-citizen', name: 'Bürger', color: 0xff0000, managed: false }

function rule(id: number, role: DiscordRole, label: string): RoleRule {
  return { id, roleId: role.id, roleName: role.name, roleColor: role.color, label, status: 'ACTIVE' }
}

function preview(changes: Partial<RulePreview> = {}): RulePreview {
  return {
    roleId: 'r-veteran',
    roleName: 'Veteran',
    managed: true,
    membersReported: true,
    usersLoaded: true,
    gains: [],
    losses: [],
    unknown: [],
    ...changes,
  }
}

function renderCard(
  rules: RoleRule[] = [],
  onCreate: (body: unknown) => Response = () => json(rules[0], 201),
  rulePreview: RulePreview = preview(),
) {
  const fetch = stubFetch({
    'GET /web/admin/role-sync/roles': () => json([citizen, veteran]),
    'GET /web/admin/role-sync/rules': () => json(rules),
    'GET /web/admin/role-sync/countries': () => json([{ id: 'de', name: 'Germany' }]),
    'POST /web/admin/role-sync/rules': (init) => onCreate(JSON.parse(String(init?.body))),
    'POST /web/admin/role-sync/rules/preview': () => json(rulePreview),
    'GET /web/admin/role-sync/rules/1/deletion-preview': () => json(rulePreview),
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

/** Confirms the dialog once the preview is loaded. */
async function confirm(name: string) {
  const dialog = await screen.findByRole('alertdialog')
  await expect.poll(() => within(dialog).getByRole('button', { name })).toBeEnabled()
  await userEvent.click(within(dialog).getByRole('button', { name }))
}

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

    // Veteran is managed already, so it is no handover.
    expect(within(await screen.findByRole('alertdialog')).getByText('Regel anlegen?')).toBeInTheDocument()
    expect(created).toEqual([])
    await confirm('Regel anlegen')
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
    await confirm('Regel anlegen')
    await expect.poll(() => created).toEqual([{ roleId: 'r-citizen', type: 'CITIZENSHIP', countryId: 'de' }])
  })

  it('shows whom the rule gives and takes the role, with names on request', async () => {
    renderCard(
      [],
      undefined,
      preview({
        gains: [
          { discordUserId: '1', name: 'Luca' },
          { discordUserId: '2', name: 'Mia' },
        ],
        losses: [{ discordUserId: '3', name: 'Ben' }],
        unknown: [{ discordUserId: '4', name: null }],
      }),
    )
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Bedingung', 'Level')
    await userEvent.click(submit())

    const dialog = await screen.findByRole('alertdialog')
    expect(await within(dialog).findByText('Gibt 2 Mitgliedern die Rolle, nimmt sie 1 weg.')).toBeInTheDocument()
    expect(within(dialog).getByText(/1 Mitglied ohne WarEra-Daten .* ist unbekannt/)).toBeInTheDocument()
    expect(within(dialog).queryByText('Ben')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Verlieren die Rolle (1)' }))
    expect(within(dialog).getByText('Ben')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Unbekannt (1)' }))
    expect(within(dialog).getByText('Unbekanntes Mitglied (4)')).toBeInTheDocument()
  })

  it('says when the preview is inaccurate or missing after a restart', async () => {
    renderCard([], undefined, preview({ usersLoaded: false }))
    await screen.findByLabelText('Rolle')
    await choose('Rolle', 'Veteran')
    await choose('Bedingung', 'Level')
    await userEvent.click(submit())

    expect(await screen.findByText(/Vorschau ungenau/)).toBeInTheDocument()
  })

  it('explains a duplicate rule', async () => {
    renderCard([], () => status(409))
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Bedingung', 'Level')
    await userEvent.click(submit())
    await confirm('Regel anlegen')

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
    const { fetch } = renderCard([rule(1, veteran, 'Level ab 10')], undefined, preview({ managed: false }))

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ löschen' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(await within(dialog).findByText(/BITS verwaltet sie nicht mehr/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringMatching(/\/rules\/1$/),
      expect.objectContaining({ method: 'DELETE' }),
    )

    await userEvent.click(screen.getByRole('button', { name: 'Regel „Level ab 10“ löschen' }))
    await confirm('Regel löschen')
    await expect
      .poll(() => fetch.mock.calls.some(([url, init]) => String(url).endsWith('/rules/1') && init?.method === 'DELETE'))
      .toBe(true)
  })

  it('shows from whom deleting the rule takes the role', async () => {
    renderCard(
      [rule(1, veteran, 'Level ab 10'), rule(2, veteran, 'Militärrang ab 20')],
      undefined,
      preview({ losses: [{ discordUserId: '3', name: 'Ben' }] }),
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ löschen' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(await within(dialog).findByText('Nimmt 1 Mitglied die Rolle weg.')).toBeInTheDocument()
  })
})
