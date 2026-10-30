import { useState } from 'react'
import { Trash2Icon } from 'lucide-react'
import { IconAction } from '@/components/IconAction'
import { Subsection } from '@/components/Subsection'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { searchMilitaryUnits, type ConditionType, type GovernmentOffice, type MuRole, type Option } from '@/lib/api'
import { needsCountry, type ConditionDraft } from './conditionDraft'
import { CONDITION_TYPES, MU_ROLES, OFFICES, errorMessage } from './labels'
import { useParties } from './queries'

const THRESHOLD_LABELS = {
  LEVEL: 'Level ab',
  MILITARY_RANK: 'Militärrang ab',
  COMPANIES: 'Firmen ab',
} as const

/** Stands for no military unit or party in the selects where that means any. */
const ANY = 'any'

/** The fields of one condition of a rule, in a group named `name`. */
export function ConditionFields({
  name,
  draft,
  onChange,
  onRemove,
  countries,
  countriesPending,
  onSearchError,
}: {
  name: string
  draft: ConditionDraft
  onChange: (draft: ConditionDraft) => void
  /** Missing for the only condition, which cannot be removed. */
  onRemove?: () => void
  countries: Option[]
  countriesPending: boolean
  onSearchError: (message: string | null) => void
}) {
  const [muQuery, setMuQuery] = useState('')
  // A saved military unit is shown until the admin searches for another.
  const [muResults, setMuResults] = useState<Option[]>(() =>
    draft.muId && draft.muName ? [{ id: draft.muId, name: draft.muName }] : [],
  )
  const { type } = draft
  const id = (field: string) => `condition-${draft.key}-${field}`
  const update = (changes: Partial<ConditionDraft>) => onChange({ ...draft, ...changes })
  const parties = useParties(type === 'PARTY' || type === 'PARTY_LEADER' ? draft.countryId : null)
  const leadership = type === 'MU_LEADERSHIP'
  const partyLeader = type === 'PARTY_LEADER'

  async function searchMu() {
    onSearchError(null)
    update({ muId: null })
    try {
      setMuResults(await searchMilitaryUnits(muQuery))
    } catch (e) {
      onSearchError(errorMessage(e))
    }
  }

  const typeItems = Object.entries(CONDITION_TYPES).map(([value, label]) => ({
    value,
    label,
  }))
  const countryItems = countries.map((c) => ({ value: c.id, label: c.name }))
  const officeItems = Object.entries(OFFICES).map(([value, label]) => ({
    value: value as GovernmentOffice,
    label,
  }))
  const muRoleItems = Object.entries(MU_ROLES).map(([value, label]) => ({
    value,
    label,
  }))
  const muItems = [
    ...(leadership ? [{ value: ANY, label: 'Jede MU' }] : []),
    ...muResults.map((mu) => ({ value: mu.id, label: mu.name })),
  ]
  const partyItems = [
    ...(partyLeader && draft.countryId ? [{ value: ANY, label: 'Jede Partei' }] : []),
    ...(parties.data ?? []).map((party) => ({
      value: party.id,
      label: party.name,
    })),
  ]
  const partyPlaceholder = !draft.countryId
    ? 'Erst Land wählen'
    : parties.isError
      ? 'Parteien nicht ladbar'
      : parties.isPending
        ? 'Parteien werden geladen …'
        : partyItems.length === 0
          ? 'Keine Parteien'
          : 'Partei wählen'

  return (
    <Subsection
      title={name}
      role="group"
      actions={
        onRemove && (
          <IconAction label={`${name} entfernen`} onClick={onRemove}>
            <Trash2Icon />
          </IconAction>
        )
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor={id('type')}>Art</FieldLabel>
          <Select
            items={typeItems}
            value={type}
            onValueChange={(value) => value && update({ type: value as ConditionType })}
          >
            <SelectTrigger id={id('type')} className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {typeItems.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        {needsCountry(type) && (
          <Field>
            <FieldLabel htmlFor={id('country')}>Land</FieldLabel>
            <SearchableSelect
              id={id('country')}
              className="w-full"
              items={countryItems}
              value={draft.countryId}
              onValueChange={(countryId) => update({ countryId, partyId: null })}
              disabled={countriesPending}
              placeholder={countriesPending ? 'Länder werden geladen …' : 'Land wählen'}
            />
          </Field>
        )}
        {type === 'GOVERNMENT_OFFICE' && (
          <FieldSet className="sm:col-span-2">
            <FieldLegend variant="label">Ämter</FieldLegend>
            <FieldGroup data-slot="checkbox-group" className="grid gap-2 sm:grid-cols-2">
              {officeItems.map((item) => (
                <Field key={item.value} orientation="horizontal">
                  <Checkbox
                    id={id(`office-${item.value}`)}
                    checked={draft.offices.includes(item.value)}
                    onCheckedChange={(checked) =>
                      update({
                        offices: checked
                          ? [...draft.offices, item.value]
                          : draft.offices.filter((office) => office !== item.value),
                      })
                    }
                  />
                  <FieldLabel htmlFor={id(`office-${item.value}`)} className="font-normal">
                    {item.label}
                  </FieldLabel>
                </Field>
              ))}
            </FieldGroup>
            <FieldDescription>Eines der gewählten Ämter genügt.</FieldDescription>
          </FieldSet>
        )}
        {leadership && (
          <Field>
            <FieldLabel htmlFor={id('mu-role')}>Stufe</FieldLabel>
            <Select
              items={muRoleItems}
              value={draft.muRole}
              onValueChange={(value) => value && update({ muRole: value as MuRole })}
            >
              <SelectTrigger id={id('mu-role')} className="w-full" aria-describedby={id('mu-role-hint')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {muRoleItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldDescription id={id('mu-role-hint')}>Höhere Stufen zählen mit.</FieldDescription>
          </Field>
        )}
        {(type === 'MILITARY_UNIT' || leadership) && (
          <>
            <Field>
              <FieldLabel htmlFor={id('mu-query')}>Military Unit suchen</FieldLabel>
              <div className="flex gap-2">
                <Input
                  id={id('mu-query')}
                  value={muQuery}
                  onChange={(event) => setMuQuery(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault()
                      searchMu()
                    }
                  }}
                />
                <Button type="button" variant="outline" onClick={searchMu} disabled={!muQuery.trim()}>
                  Suchen
                </Button>
              </div>
            </Field>
            <Field>
              <FieldLabel htmlFor={id('mu')}>Military Unit</FieldLabel>
              <Select
                items={muItems}
                value={leadership ? (draft.muId ?? ANY) : draft.muId}
                onValueChange={(muId) => update({ muId: muId === ANY ? null : muId })}
                disabled={muItems.length === 0}
              >
                <SelectTrigger id={id('mu')} className="w-full">
                  <SelectValue placeholder={muItems.length === 0 ? 'Erst suchen' : 'MU wählen'} />
                </SelectTrigger>
                <SelectContent>
                  {muItems.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </>
        )}
        {(type === 'PARTY' || partyLeader) && (
          <Field>
            <FieldLabel htmlFor={id('party')}>Partei</FieldLabel>
            <SearchableSelect
              id={id('party')}
              className="w-full"
              items={partyItems}
              value={partyLeader && draft.countryId ? (draft.partyId ?? ANY) : draft.partyId}
              onValueChange={(partyId) => update({ partyId: partyId === ANY ? null : partyId })}
              disabled={partyItems.length === 0}
              placeholder={partyPlaceholder}
            />
          </Field>
        )}
        {(type === 'LEVEL' || type === 'MILITARY_RANK' || type === 'COMPANIES') && (
          <Field>
            <FieldLabel htmlFor={id('threshold')}>{THRESHOLD_LABELS[type]}</FieldLabel>
            <Input
              id={id('threshold')}
              type="number"
              min={1}
              value={draft.threshold}
              onChange={(event) => update({ threshold: event.target.value })}
            />
          </Field>
        )}
      </div>
    </Subsection>
  )
}
