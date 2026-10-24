import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Trash2Icon } from 'lucide-react'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  ApiError,
  createRoleRule,
  deleteRoleRule,
  getCountries,
  getRoleRules,
  getRoles,
  searchMilitaryUnits,
  type ConditionType,
  type CreateRoleRule,
  type DiscordRole,
  type GovernmentOffice,
  type Option,
  type RoleRule,
  type RuleStatus,
} from '@/lib/api'

const CONDITION_TYPES: Record<ConditionType, string> = {
  CITIZENSHIP: 'Staatsbürgerschaft',
  GOVERNMENT_OFFICE: 'Regierungsamt',
  MILITARY_UNIT: 'Military Unit',
  LEVEL: 'Level',
  MILITARY_RANK: 'Militärrang',
}

const OFFICES: Record<GovernmentOffice, string> = {
  PRESIDENT: 'Präsident',
  VICE_PRESIDENT: 'Vizepräsident',
  MINISTER_OF_ECONOMY: 'Wirtschaftsminister',
  MINISTER_OF_DEFENSE: 'Verteidigungsminister',
  MINISTER_OF_FOREIGN_AFFAIRS: 'Außenminister',
  CONGRESS: 'Kongress',
}

const STATUS: Record<RuleStatus, { label: string; variant: 'secondary' | 'destructive' | 'outline' }> = {
  ACTIVE: { label: 'Aktiv', variant: 'secondary' },
  ORPHANED: { label: 'Verwaist', variant: 'destructive' },
  NOT_ASSIGNABLE: { label: 'Nicht vergebbar', variant: 'outline' },
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'WarEra ist gerade nicht erreichbar. Bitte versuche es später erneut.'
    if (error.status === 400) return 'Die Regel ist unvollständig oder die Rolle kann nicht vergeben werden.'
  }
  return 'Etwas ist schiefgelaufen. Bitte versuche es später erneut.'
}

function RoleName({ name, color }: { name: string; color: number | null }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className="size-2.5 shrink-0 rounded-full bg-muted-foreground/40"
        style={color !== null ? { backgroundColor: `#${color.toString(16).padStart(6, '0')}` } : undefined}
      />
      {name}
    </span>
  )
}

/** Rules that tie Discord roles to WarEra data; BITS gives and takes these roles on its own. */
export function RoleRulesCard() {
  const [roles, setRoles] = useState<DiscordRole[]>([])
  const [rules, setRules] = useState<RoleRule[]>([])
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(
    () =>
      Promise.all([getRoles(), getRoleRules()])
        .then(([loadedRoles, loadedRules]) => {
          setRoles(loadedRoles)
          setRules(loadedRules)
        })
        .catch((e) => setError(errorMessage(e))),
    [],
  )

  useEffect(() => {
    reload()
  }, [reload])

  async function onDelete(id: number) {
    setError(null)
    try {
      await deleteRoleRule(id)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Automatische Rollen</CardTitle>
        <CardDescription>
          BITS vergibt diese Rollen anhand der WarEra-Daten verknüpfter Accounts und entzieht sie allen, die keine
          ihrer Regeln erfüllen. Mehrere Regeln für dieselbe Rolle reichen einzeln aus.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {rules.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine Regeln angelegt.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rolle</TableHead>
                <TableHead>Bedingung</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-0" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.map((rule) => (
                <TableRow key={rule.id}>
                  <TableCell>
                    {rule.roleName !== null ? (
                      <RoleName name={rule.roleName} color={rule.roleColor} />
                    ) : (
                      <span className="text-muted-foreground">Gelöschte Rolle</span>
                    )}
                  </TableCell>
                  <TableCell>{rule.label}</TableCell>
                  <TableCell>
                    <Badge variant={STATUS[rule.status].variant}>{STATUS[rule.status].label}</Badge>
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Regel löschen"
                      onClick={() => onDelete(rule.id)}
                    >
                      <Trash2Icon />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <NewRuleForm roles={roles} onCreated={reload} />
      </CardContent>
    </Card>
  )
}

function NewRuleForm({ roles, onCreated }: { roles: DiscordRole[]; onCreated: () => Promise<void> }) {
  const [roleId, setRoleId] = useState<string | null>(null)
  const [type, setType] = useState<ConditionType>('CITIZENSHIP')
  const [countries, setCountries] = useState<Option[]>([])
  const [countryId, setCountryId] = useState<string | null>(null)
  const [office, setOffice] = useState<GovernmentOffice>('PRESIDENT')
  const [muQuery, setMuQuery] = useState('')
  const [muResults, setMuResults] = useState<Option[]>([])
  const [muId, setMuId] = useState<string | null>(null)
  const [threshold, setThreshold] = useState('1')
  const [confirming, setConfirming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const needsCountry = type === 'CITIZENSHIP' || type === 'GOVERNMENT_OFFICE'

  useEffect(() => {
    if (!needsCountry || countries.length > 0) return
    getCountries()
      .then(setCountries)
      .catch((e) => setError(errorMessage(e)))
  }, [needsCountry, countries.length])

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
    // The first rule of a role hands the role over to BITS, which may take it from many members at once.
    if (role && !role.managed) setConfirming(true)
    else create()
  }

  async function create() {
    const newRule = rule()
    if (!newRule) return
    setConfirming(false)
    setSubmitting(true)
    setError(null)
    try {
      await createRoleRule(newRule)
      await onCreated()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  const roleItems = roles.map((r) => ({ value: r.id, label: r.name }))
  const typeItems = Object.entries(CONDITION_TYPES).map(([value, label]) => ({ value, label }))
  const countryItems = countries.map((c) => ({ value: c.id, label: c.name }))
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
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label>Rolle</Label>
          <Select items={roleItems} value={roleId} onValueChange={setRoleId}>
            <SelectTrigger className="w-full">
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
          <Label>Bedingung</Label>
          <Select
            items={typeItems}
            value={type}
            onValueChange={(value) => value && setType(value as ConditionType)}
          >
            <SelectTrigger className="w-full">
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
            <Label>Land</Label>
            <Select items={countryItems} value={countryId} onValueChange={setCountryId}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Land wählen" />
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
            <Label>Amt</Label>
            <Select
              items={officeItems}
              value={office}
              onValueChange={(value) => value && setOffice(value as GovernmentOffice)}
            >
              <SelectTrigger className="w-full">
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
              <Label>Military Unit</Label>
              <Select items={muItems} value={muId} onValueChange={setMuId} disabled={muItems.length === 0}>
                <SelectTrigger className="w-full">
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
      <Button type="submit" className="self-start" disabled={submitting || rule() === null}>
        Regel anlegen
      </Button>
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Rolle an BITS übergeben?</AlertDialogTitle>
            <AlertDialogDescription>
              Die Rolle {role?.name} wird ab jetzt von BITS verwaltet; Mitglieder ohne passende Regel verlieren sie.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction onClick={create}>Regel anlegen</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </form>
  )
}
