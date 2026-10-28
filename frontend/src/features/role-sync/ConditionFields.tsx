import { useState } from 'react'
import { Trash2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { searchMilitaryUnits, type ConditionType, type GovernmentOffice, type MuRole, type Option } from '@/lib/api'
import { needsCountry, type ConditionDraft } from './conditionDraft'
import { CONDITION_TYPES, MU_ROLES, OFFICES, errorMessage } from './labels'
import { useParties } from './queries'

const THRESHOLD_LABELS = { LEVEL: 'Level ab', MILITARY_RANK: 'Militärrang ab', COMPANIES: 'Firmen ab' } as const

/** Stands for no military unit or party in the selects where that means any. */
const ANY = 'any'

/** The fields of one condition of a new rule, in a group named `name`. */
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
  const [muResults, setMuResults] = useState<Option[]>([])
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
    value,
    label,
  }))
  const muRoleItems = Object.entries(MU_ROLES).map(([value, label]) => ({ value, label }))
  const muItems = [
    ...(leadership ? [{ value: ANY, label: 'Jede MU' }] : []),
    ...muResults.map((mu) => ({ value: mu.id, label: mu.name })),
  ]
  const partyItems = [
    ...(partyLeader && draft.countryId ? [{ value: ANY, label: 'Jede Partei' }] : []),
    ...(parties.data ?? []).map((party) => ({ value: party.id, label: party.name })),
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
    // The legend floats so it sits inside the border; it must stay the first child to name the group.
    <fieldset className="relative rounded-md border p-4">
      <legend className="float-left mb-3 text-sm font-medium">{name}</legend>
      {onRemove && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute top-2.5 right-2.5"
          aria-label={`${name} entfernen`}
          onClick={onRemove}
        >
          <Trash2Icon />
        </Button>
      )}
      <div className="clear-left grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={id('type')}>Art</Label>
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
        </div>
        {needsCountry(type) && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={id('country')}>Land</Label>
            <Select
              items={countryItems}
              value={draft.countryId}
              onValueChange={(countryId) => update({ countryId, partyId: null })}
              disabled={countriesPending}
            >
              <SelectTrigger id={id('country')} className="w-full">
                <SelectValue placeholder={countriesPending ? 'Länder werden geladen …' : 'Land wählen'} />
              </SelectTrigger>
              <SelectContent>
                {countryItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        {type === 'GOVERNMENT_OFFICE' && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={id('office')}>Amt</Label>
            <Select
              items={officeItems}
              value={draft.office}
              onValueChange={(value) => value && update({ office: value as GovernmentOffice })}
            >
              <SelectTrigger id={id('office')} className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {officeItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        {leadership && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={id('mu-role')}>Stufe</Label>
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
            <p id={id('mu-role-hint')} className="text-sm text-muted-foreground">
              Höhere Stufen zählen mit.
            </p>
          </div>
        )}
        {(type === 'MILITARY_UNIT' || leadership) && (
          <>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={id('mu-query')}>Military Unit suchen</Label>
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
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={id('mu')}>Military Unit</Label>
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
            </div>
          </>
        )}
        {(type === 'PARTY' || partyLeader) && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={id('party')}>Partei</Label>
            <Select
              items={partyItems}
              value={partyLeader && draft.countryId ? (draft.partyId ?? ANY) : draft.partyId}
              onValueChange={(partyId) => update({ partyId: partyId === ANY ? null : partyId })}
              disabled={partyItems.length === 0}
            >
              <SelectTrigger id={id('party')} className="w-full">
                <SelectValue placeholder={partyPlaceholder} />
              </SelectTrigger>
              <SelectContent>
                {partyItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        {(type === 'LEVEL' || type === 'MILITARY_RANK' || type === 'COMPANIES') && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={id('threshold')}>{THRESHOLD_LABELS[type]}</Label>
            <Input
              id={id('threshold')}
              type="number"
              min={1}
              value={draft.threshold}
              onChange={(event) => update({ threshold: event.target.value })}
            />
          </div>
        )}
      </div>
    </fieldset>
  )
}
