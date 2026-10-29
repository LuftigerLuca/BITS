import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { createQueryClient } from '@/lib/queryClient'
import type { DiscordRole, RoleRule, RuleCondition, RulePreview } from '@/lib/api'
import { json, status, stubFetch } from '@/test/fetch'
import { expectToast } from '@/test/toast'
import { NewRuleCard } from './NewRuleCard'
import { RoleRulesCard } from './RoleRulesCard'

const veteran: DiscordRole = { id: 'r-veteran', name: 'Veteran', color: null, managed: true, dskName: null }
const citizen: DiscordRole = { id: 'r-citizen', name: 'Bürger', color: 0xff0000, managed: false, dskName: null }

function rule(role: DiscordRole, label: string, blocks: RoleRule['blocks'] = [{ label, conditions: [] }]): RoleRule {
  return { roleId: role.id, roleName: role.name, roleColor: role.color, label, blocks, status: 'ACTIVE' }
}

function condition(fields: Partial<RuleCondition> & Pick<RuleCondition, 'type' | 'label'>): RuleCondition {
  return { countryId: null, offices: [], muId: null, threshold: null, partyId: null, muRole: null, targetName: null, ...fields }
}

const level10 = rule(veteran, 'Level ab 10', [
  { label: 'Level ab 10', conditions: [condition({ type: 'LEVEL', threshold: 10, label: 'Level ab 10' })] },
])

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
  onDelete: () => Response = () => status(204),
  onUpdate: (body: unknown) => Response = () => json(rules[0]),
) {
  const fetch = stubFetch({
    'GET /web/admin/role-sync/roles': () => json([citizen, veteran]),
    'GET /web/admin/role-sync/rules': () => json(rules),
    'GET /web/admin/role-sync/countries': () => json([{ id: 'de', name: 'Germany' }]),
    'GET /web/admin/role-sync/countries/de/parties': () => json([{ id: 'p1', name: 'Zentrum' }]),
    'GET /web/admin/role-sync/military-units': () => json([{ id: 'm1', name: 'Deutsche Marine' }]),
    'POST /web/admin/role-sync/rules': (init) => onCreate(JSON.parse(String(init?.body))),
    'POST /web/admin/role-sync/rules/preview': () => json(rulePreview),
    'GET /web/admin/role-sync/rules/r-veteran/deletion-preview': () => json(rulePreview),
    'DELETE /web/admin/role-sync/rules/r-veteran': onDelete,
    'PUT /web/admin/role-sync/rules/r-veteran': (init) => onUpdate(JSON.parse(String(init?.body))),
  })
  const queryClient = createQueryClient()
  queryClient.setDefaultOptions({ queries: { retry: false } })
  const { container } = render(
    <QueryClientProvider client={queryClient}>
      <RolesCards />
      <Toaster />
    </QueryClientProvider>,
  )
  return { fetch, container }
}

/** As on the page, which ties the edit buttons of the list to the form. */
function RolesCards() {
  const [editing, setEditing] = useState<RoleRule | null>(null)
  return (
    <>
      <RoleRulesCard onEdit={setEditing} />
      <NewRuleCard editing={editing} onEdit={setEditing} onDone={() => setEditing(null)} />
    </>
  )
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
      return json(rule(veteran, 'Level ab 5'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'Level')
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
    await expect.poll(() => created).toEqual([{ roleId: 'r-veteran', blocks: [[{ type: 'LEVEL', threshold: 5 }]] }])
    await expectToast('success', 'Regel angelegt')
  })

  it('needs a country for citizenship rules and confirms the first rule of a role', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(citizen, 'Staatsbürgerschaft Germany'), 201)
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
    await expect.poll(() => created).toEqual([{ roleId: 'r-citizen', blocks: [[{ type: 'CITIZENSHIP', countryId: 'de' }]] }])
  })

  it('combines several conditions in one rule and removes them again', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(citizen, 'Staatsbürgerschaft Germany und Level ab 10'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Bürger')
    await choose('Land', 'Germany')
    expect(screen.queryByRole('button', { name: 'Bedingung 1 entfernen' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    // A new condition asks for a country first, so the rule is incomplete.
    expect(submit()).toBeDisabled()

    const second = screen.getByRole('group', { name: 'Bedingung 2' })
    await userEvent.click(within(second).getByLabelText('Art'))
    await userEvent.click(await screen.findByRole('option', { name: 'Level' }))
    const threshold = within(second).getByLabelText('Level ab')
    await userEvent.clear(threshold)
    await userEvent.type(threshold, '10')
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung 3 entfernen' }))
    await userEvent.click(submit())

    await confirm('Regel anlegen')
    await expect.poll(() => created).toEqual([
      {
        roleId: 'r-citizen',
        blocks: [
          [
            { type: 'CITIZENSHIP', countryId: 'de' },
            { type: 'LEVEL', threshold: 10 },
          ],
        ],
      },
    ])
  })

  it('asks for the country and then a party of it for party rules', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(veteran, 'Mitglied der Partei Zentrum'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'Partei')
    expect(screen.getByLabelText('Partei')).toHaveTextContent('Erst Land wählen')
    await choose('Land', 'Germany')
    expect(submit()).toBeDisabled()
    await choose('Partei', 'Zentrum')
    await userEvent.click(submit())

    await confirm('Regel anlegen')
    await expect.poll(() => created).toEqual([
      { roleId: 'r-veteran', blocks: [[{ type: 'PARTY', countryId: 'de', partyId: 'p1' }]] },
    ])
  })

  it('takes a rank and optionally a military unit for MU leadership rules', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(veteran, 'Manager einer MU'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'MU-Führung')
    expect(screen.getByLabelText('Military Unit')).toHaveTextContent('Jede MU')
    expect(screen.getByLabelText('Stufe')).toHaveAccessibleDescription('Höhere Stufen zählen mit.')
    await choose('Stufe', 'Manager')
    await userEvent.click(submit())
    await confirm('Regel anlegen')

    // The form starts afresh for the next rule.
    await expect.poll(() => screen.getByLabelText('Rolle')).toHaveTextContent('Rolle wählen')
    await choose('Rolle', 'Veteran')
    await choose('Art', 'MU-Führung')
    await choose('Stufe', 'Manager')
    await userEvent.type(screen.getByLabelText('Military Unit suchen'), 'Marine')
    await userEvent.click(screen.getByRole('button', { name: 'Suchen' }))
    await choose('Military Unit', 'Deutsche Marine')
    await userEvent.click(submit())
    await confirm('Regel anlegen')

    await expect.poll(() => created).toEqual([
      { roleId: 'r-veteran', blocks: [[{ type: 'MU_LEADERSHIP', muRole: 'MANAGER' }]] },
      { roleId: 'r-veteran', blocks: [[{ type: 'MU_LEADERSHIP', muId: 'm1', muRole: 'MANAGER' }]] },
    ])
  })

  it('takes a country and optionally a party for party leader rules', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(veteran, 'Parteivorsitz in Germany'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'Parteivorsitz')
    expect(screen.getByLabelText('Partei')).toHaveTextContent('Erst Land wählen')
    expect(submit()).toBeDisabled()
    await choose('Land', 'Germany')
    expect(screen.getByLabelText('Partei')).toHaveTextContent('Jede Partei')
    await userEvent.click(submit())
    await confirm('Regel anlegen')

    await expect.poll(() => screen.getByLabelText('Rolle')).toHaveTextContent('Rolle wählen')
    await choose('Rolle', 'Veteran')
    await choose('Art', 'Parteivorsitz')
    await choose('Land', 'Germany')
    await choose('Partei', 'Zentrum')
    await userEvent.click(submit())
    await confirm('Regel anlegen')

    await expect.poll(() => created).toEqual([
      { roleId: 'r-veteran', blocks: [[{ type: 'PARTY_LEADER', countryId: 'de' }]] },
      { roleId: 'r-veteran', blocks: [[{ type: 'PARTY_LEADER', countryId: 'de', partyId: 'p1' }]] },
    ])
  })

  it('takes one or more offices, by rank, for office rules', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(veteran, 'Präsident oder Vizepräsident in Germany'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'Regierungsamt')
    await choose('Land', 'Germany')
    expect(submit()).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Vizepräsident' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Außenminister' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Präsident' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Außenminister' }))
    expect(screen.getByRole('checkbox', { name: 'Außenminister' })).not.toBeChecked()
    await userEvent.click(submit())

    await confirm('Regel anlegen')
    await expect.poll(() => created).toEqual([
      {
        roleId: 'r-veteran',
        blocks: [[{ type: 'GOVERNMENT_OFFICE', countryId: 'de', offices: ['PRESIDENT', 'VICE_PRESIDENT'] }]],
      },
    ])
  })

  it('takes a number of companies', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(veteran, 'Besitz von mindestens 2 Firmen'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'Firmenbesitz')
    const threshold = screen.getByLabelText('Firmen ab')
    await userEvent.clear(threshold)
    await userEvent.type(threshold, '2')
    await userEvent.click(submit())

    await confirm('Regel anlegen')
    await expect.poll(() => created).toEqual([{ roleId: 'r-veteran', blocks: [[{ type: 'COMPANIES', threshold: 2 }]] }])
  })

  it('does not take the same condition twice', async () => {
    renderCard()
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Land', 'Germany')
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    const second = screen.getByRole('group', { name: 'Bedingung 2' })
    await userEvent.click(within(second).getByLabelText('Land'))
    await userEvent.click(await screen.findByRole('option', { name: 'Germany' }))

    expect(screen.getByText('Eine Bedingung steht doppelt in einem Block.')).toBeInTheDocument()
    expect(submit()).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung 1 entfernen' }))
    expect(submit()).toBeEnabled()
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
    await choose('Art', 'Level')
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
    await choose('Art', 'Level')
    await userEvent.click(submit())

    expect(await screen.findByText(/Vorschau ungenau/)).toBeInTheDocument()
  })

  it('explains in a toast that the role has a rule already', async () => {
    renderCard([], () => status(409))
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Veteran')
    await choose('Art', 'Level')
    await userEvent.click(submit())
    await confirm('Regel anlegen')

    await expectToast('error', 'Für diese Rolle gibt es schon eine Regel, oder sie gehört zu einer DSK.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('combines conditions with AND before OR, as blocks', async () => {
    const created: unknown[] = []
    renderCard([], (body) => {
      created.push(body)
      return json(rule(citizen, 'Staatsbürgerschaft Germany und Level ab 10 oder Mitglied der MU Deutsche Marine'), 201)
    })
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Bürger')
    await choose('Land', 'Germany')
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    const second = screen.getByRole('group', { name: 'Bedingung 2' })
    await userEvent.click(within(second).getByLabelText('Art'))
    await userEvent.click(await screen.findByRole('option', { name: 'Level' }))
    const threshold = within(second).getByLabelText('Level ab')
    await userEvent.clear(threshold)
    await userEvent.type(threshold, '10')
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    const operator = screen.getByRole('group', { name: 'Verknüpfung vor Bedingung 3' })
    expect(within(operator).getByRole('button', { name: 'und' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(within(operator).getByRole('button', { name: 'oder' }))
    // The OR is drawn as a line between the blocks.
    expect(screen.getAllByRole('separator')).toHaveLength(2)
    const third = screen.getByRole('group', { name: 'Bedingung 3' })
    await userEvent.click(within(third).getByLabelText('Art'))
    await userEvent.click(await screen.findByRole('option', { name: 'Military Unit' }))
    await userEvent.type(within(third).getByLabelText('Military Unit suchen'), 'Marine')
    await userEvent.click(within(third).getByRole('button', { name: 'Suchen' }))
    await userEvent.click(within(third).getByLabelText('Military Unit'))
    await userEvent.click(await screen.findByRole('option', { name: 'Deutsche Marine' }))
    await userEvent.click(submit())

    await confirm('Regel anlegen')
    await expect.poll(() => created).toEqual([
      {
        roleId: 'r-citizen',
        blocks: [
          [
            { type: 'CITIZENSHIP', countryId: 'de' },
            { type: 'LEVEL', threshold: 10 },
          ],
          [{ type: 'MILITARY_UNIT', muId: 'm1' }],
        ],
      },
    ])
  })

  it('does not take the same block twice', async () => {
    renderCard()
    await screen.findByLabelText('Rolle')

    await choose('Rolle', 'Bürger')
    await choose('Land', 'Germany')
    await userEvent.click(screen.getByRole('button', { name: 'Bedingung hinzufügen' }))
    const second = screen.getByRole('group', { name: 'Bedingung 2' })
    await userEvent.click(within(second).getByLabelText('Land'))
    await userEvent.click(await screen.findByRole('option', { name: 'Germany' }))
    const operator = screen.getByRole('group', { name: 'Verknüpfung vor Bedingung 2' })
    await userEvent.click(within(operator).getByRole('button', { name: 'oder' }))

    expect(screen.getByText('Ein Block steht doppelt in der Regel.')).toBeInTheDocument()
    expect(screen.queryByText('Eine Bedingung steht doppelt in einem Block.')).not.toBeInTheDocument()
    expect(submit()).toBeDisabled()
  })

  it('leads to editing the rule a role has already', async () => {
    renderCard([level10])
    await screen.findByText('Level ab 10')

    await choose('Rolle', 'Veteran')
    expect(screen.getByText(/Für diese Rolle gibt es schon eine Regel/)).toBeInTheDocument()
    expect(submit()).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Regel bearbeiten' }))

    expect(await screen.findByText('Regel für Veteran bearbeiten')).toBeInTheDocument()
    expect(screen.getByLabelText('Level ab')).toHaveValue(10)
  })

  it('edits a rule as a whole after confirmation, starting from its blocks', async () => {
    const updated: unknown[] = []
    const marine = rule(veteran, 'Staatsbürgerschaft Germany und Level ab 10 oder Mitglied der MU Deutsche Marine', [
      {
        label: 'Staatsbürgerschaft Germany und Level ab 10',
        conditions: [
          condition({ type: 'CITIZENSHIP', countryId: 'de', targetName: 'Germany', label: 'Staatsbürgerschaft Germany' }),
          condition({ type: 'LEVEL', threshold: 10, label: 'Level ab 10' }),
        ],
      },
      {
        label: 'Mitglied der MU Deutsche Marine',
        conditions: [
          condition({ type: 'MILITARY_UNIT', muId: 'm1', targetName: 'Deutsche Marine', label: 'Mitglied der MU Deutsche Marine' }),
        ],
      },
    ])
    renderCard([marine], undefined, preview({ losses: [{ discordUserId: '3', name: 'Ben' }] }), undefined, (body) => {
      updated.push(body)
      return json(marine)
    })

    await userEvent.click(await screen.findByRole('button', { name: `Regel „${marine.label}“ bearbeiten` }))
    expect(await screen.findByText('Regel für Veteran bearbeiten')).toBeInTheDocument()
    expect(screen.getByLabelText('Rolle')).toBeDisabled()
    const first = screen.getByRole('group', { name: 'Bedingung 1' })
    expect(within(first).getByLabelText('Land')).toHaveTextContent('Germany')
    const third = screen.getByRole('group', { name: 'Bedingung 3' })
    expect(within(third).getByLabelText('Military Unit')).toHaveTextContent('Deutsche Marine')
    const operator = screen.getByRole('group', { name: 'Verknüpfung vor Bedingung 3' })
    expect(within(operator).getByRole('button', { name: 'oder' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(within(operator).getByRole('button', { name: 'und' }))
    await userEvent.click(screen.getByRole('button', { name: 'Regel speichern' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(await within(dialog).findByText('Nimmt 1 Mitglied die Rolle weg.')).toBeInTheDocument()
    expect(updated).toEqual([])
    await confirm('Regel speichern')
    await expect.poll(() => updated).toEqual([
      {
        blocks: [
          [
            { type: 'CITIZENSHIP', countryId: 'de' },
            { type: 'LEVEL', threshold: 10 },
            { type: 'MILITARY_UNIT', muId: 'm1' },
          ],
        ],
      },
    ])
    await expectToast('success', 'Regel gespeichert')
    expect(await screen.findByText('Neue Regel')).toBeInTheDocument()
  })

  it('cancels editing without saving', async () => {
    const { fetch } = renderCard([level10])

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ bearbeiten' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }))

    expect(await screen.findByText('Neue Regel')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ method: 'PUT' }))
  })

  it('names every field', async () => {
    const { container } = renderCard()
    await screen.findByLabelText('Rolle')
    await choose('Art', 'Regierungsamt')

    const results = await axe.run(container, { runOnly: ['label', 'button-name', 'select-name', 'aria-input-field-name'] })

    expect(results.violations).toEqual([])
    expect(within(screen.getByRole('group', { name: 'Ämter' })).getByRole('checkbox', { name: 'Präsident' })).toBeInTheDocument()
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

  it('deletes a rule only after confirmation, and says that BITS no longer manages its role', async () => {
    const { fetch } = renderCard([rule(veteran, 'Level ab 10')], undefined, preview({ managed: false }))

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ löschen' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(await within(dialog).findByText(/BITS verwaltet sie nicht mehr/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringMatching(/\/rules\/r-veteran$/),
      expect.objectContaining({ method: 'DELETE' }),
    )

    await userEvent.click(screen.getByRole('button', { name: 'Regel „Level ab 10“ löschen' }))
    await confirm('Regel löschen')
    await expect
      .poll(() => fetch.mock.calls.some(([url, init]) => String(url).endsWith('/rules/r-veteran') && init?.method === 'DELETE'))
      .toBe(true)
    await expectToast('success', 'Regel gelöscht')
  })

  it('says in a toast when a rule cannot be deleted', async () => {
    renderCard([rule(veteran, 'Level ab 10')], undefined, preview(), () => status(500))

    await userEvent.click(await screen.findByRole('button', { name: 'Regel „Level ab 10“ löschen' }))
    await confirm('Regel löschen')

    await expectToast('error', 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the blocks of a rule with „oder“ between them', async () => {
    renderCard([
      rule(veteran, 'Level ab 10 oder Militärrang ab 20', [
        { label: 'Level ab 10', conditions: [] },
        { label: 'Militärrang ab 20', conditions: [] },
      ]),
    ])

    const row = (await screen.findByText('Level ab 10')).closest('tr')!
    expect(row).toHaveTextContent('Level ab 10oderMilitärrang ab 20')
  })
})
