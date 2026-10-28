import { useState } from 'react'
import { Trash2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { searchMilitaryUnits, type ConditionType, type GovernmentOffice, type Option } from '@/lib/api'
import { needsCountry, type ConditionDraft } from './conditionDraft'
import { CONDITION_TYPES, OFFICES, errorMessage } from './labels'
import { useParties } from './queries'

const THRESHOLD_LABELS = { LEVEL: 'Level ab', MILITARY_RANK: 'Militärrang ab', COMPANIES: 'Firmen ab' } as const

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
  const parties = useParties(type === 'PARTY' ? draft.countryId : null)

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
  const muItems = muResults.map((mu) => ({ value: mu.id, label: mu.name }))
  const partyItems = (parties.data ?? []).map((party) => ({ value: party.id, label: party.name }))
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
            <SearchableSelect
              id={id('country')}
              className="w-full"
              items={countryItems}
              value={draft.countryId}
              onValueChange={(countryId) => update({ countryId, partyId: null })}
              disabled={countriesPending}
              placeholder={countriesPending ? 'Länder werden geladen …' : 'Land wählen'}
            />
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
        {type === 'MILITARY_UNIT' && (
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
                value={draft.muId}
                onValueChange={(muId) => update({ muId })}
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
        {type === 'PARTY' && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={id('party')}>Partei</Label>
            <SearchableSelect
              id={id('party')}
              className="w-full"
              items={partyItems}
              value={draft.partyId}
              onValueChange={(partyId) => update({ partyId })}
              disabled={partyItems.length === 0}
              placeholder={partyPlaceholder}
            />
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
