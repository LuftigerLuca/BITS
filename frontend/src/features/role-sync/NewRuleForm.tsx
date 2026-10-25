import { useState, type FormEvent } from 'react'
import { ErrorState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useCountries } from '@/lib/countries'
import {
  searchMilitaryUnits,
  type ConditionType,
  type CreateRoleRule,
  type DiscordRole,
  type GovernmentOffice,
  type Option,
} from '@/lib/api'
import { CONDITION_TYPES, OFFICES, errorMessage } from './labels'
import { RulePreview } from './RulePreview'
import { useCreatePreview, useCreateRoleRule } from './queries'

/** Form for a new rule; it shows whom the rule gives and takes the role and saves it only once confirmed. */
export function NewRuleForm({ roles }: { roles: DiscordRole[] }) {
  const [roleId, setRoleId] = useState<string | null>(null)
  const [type, setType] = useState<ConditionType>('CITIZENSHIP')
  const [countryId, setCountryId] = useState<string | null>(null)
  const [office, setOffice] = useState<GovernmentOffice>('PRESIDENT')
  const [muQuery, setMuQuery] = useState('')
  const [muResults, setMuResults] = useState<Option[]>([])
  const [muId, setMuId] = useState<string | null>(null)
  const [threshold, setThreshold] = useState('1')
  /** The rule waiting for confirmation. */
  const [pending, setPending] = useState<CreateRoleRule | null>(null)
  const [error, setError] = useState<string | null>(null)
  const createRule = useCreateRoleRule()
  const preview = useCreatePreview(pending)

  const needsCountry = type === 'CITIZENSHIP' || type === 'GOVERNMENT_OFFICE'
  const countries = useCountries(needsCountry)

  const role = roles.find((r) => r.id === roleId)

  function rule(): CreateRoleRule | null {
    if (!roleId) return null
    switch (type) {
      case 'CITIZENSHIP':
        return countryId ? { roleId, type, countryId } : null
      case 'GOVERNMENT_OFFICE':
        return countryId ? { roleId, type, countryId, office } : null
      case 'MILITARY_UNIT':
        return muId ? { roleId, type, muId } : null
      case 'LEVEL':
      case 'MILITARY_RANK': {
        const value = Number(threshold)
        return Number.isInteger(value) && value >= 1 ? { roleId, type, threshold: value } : null
      }
    }
  }

  async function searchMu() {
    setError(null)
    setMuId(null)
    try {
      setMuResults(await searchMilitaryUnits(muQuery))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setPending(rule())
  }

  function create() {
    if (!pending) return
    setPending(null)
    createRule.mutate(pending, { onError: (e) => setError(errorMessage(e)) })
  }

  const roleItems = roles.map((r) => ({ value: r.id, label: r.name }))
  const typeItems = Object.entries(CONDITION_TYPES).map(([value, label]) => ({ value, label }))
  const countryItems = (countries.data ?? []).map((c) => ({ value: c.id, label: c.name }))
  const officeItems = Object.entries(OFFICES).map(([value, label]) => ({ value, label }))
  const muItems = muResults.map((mu) => ({ value: mu.id, label: mu.name }))

  return (
    <form className="flex flex-col gap-4 border-t pt-6" onSubmit={submit}>
      <p className="text-sm font-medium">Neue Regel</p>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {countries.isError && (
        <ErrorState message={errorMessage(countries.error)} onRetry={() => countries.refetch()} />
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="rule-role">Rolle</Label>
          <Select items={roleItems} value={roleId} onValueChange={setRoleId}>
            <SelectTrigger id="rule-role" className="w-full">
              <SelectValue placeholder="Rolle wählen" />
            </SelectTrigger>
            <SelectContent>
              {roles.map((r) => (
                <SelectItem key={r.id} value={r.id}>
                  <RoleName name={r.name} color={r.color} />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            Nur Rollen unter der höchsten Rolle des Bots, ohne @everyone und Integrationsrollen.
          </p>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="rule-type">Bedingung</Label>
          <Select
            items={typeItems}
            value={type}
            onValueChange={(value) => value && setType(value as ConditionType)}
          >
            <SelectTrigger id="rule-type" className="w-full">
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
        {needsCountry && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="rule-country">Land</Label>
            <Select items={countryItems} value={countryId} onValueChange={setCountryId} disabled={countries.isPending}>
              <SelectTrigger id="rule-country" className="w-full">
                <SelectValue placeholder={countries.isPending ? 'Länder werden geladen …' : 'Land wählen'} />
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
            <Label htmlFor="rule-office">Amt</Label>
            <Select
              items={officeItems}
              value={office}
              onValueChange={(value) => value && setOffice(value as GovernmentOffice)}
            >
              <SelectTrigger id="rule-office" className="w-full">
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
              <Label htmlFor="mu-query">Military Unit suchen</Label>
              <div className="flex gap-2">
                <Input
                  id="mu-query"
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
              <Label htmlFor="rule-mu">Military Unit</Label>
              <Select items={muItems} value={muId} onValueChange={setMuId} disabled={muItems.length === 0}>
                <SelectTrigger id="rule-mu" className="w-full">
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
        {(type === 'LEVEL' || type === 'MILITARY_RANK') && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="threshold">{type === 'LEVEL' ? 'Level ab' : 'Militärrang ab'}</Label>
            <Input
              id="threshold"
              type="number"
              min={1}
              value={threshold}
              onChange={(event) => setThreshold(event.target.value)}
            />
          </div>
        )}
      </div>
      <Button type="submit" className="self-start" disabled={createRule.isPending || rule() === null}>
        Regel anlegen
      </Button>
      <AlertDialog open={pending !== null} onOpenChange={(open) => !open && setPending(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            {/* The first rule of a role hands the role over to BITS, which takes it from everybody without a match. */}
            <AlertDialogTitle>{role?.managed ? 'Regel anlegen?' : 'Rolle an BITS übergeben?'}</AlertDialogTitle>
            <AlertDialogDescription>
              {role?.managed
                ? `BITS gleicht die Rolle ${role.name} danach sofort ab.`
                : `Die Rolle ${role?.name} wird ab jetzt von BITS verwaltet; Mitglieder ohne passende Regel verlieren sie, auch wenn sie sie von Hand bekommen haben.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <RulePreview preview={preview} />
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction onClick={create} disabled={preview.isPending}>
              Regel anlegen
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </form>
  )
}
