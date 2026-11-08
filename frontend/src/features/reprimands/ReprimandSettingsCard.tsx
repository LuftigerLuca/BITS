import { useState } from 'react'
import { Link } from 'react-router'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { ReprimandSettings } from './api'
import { MAX_TIMEOUT_MINUTES, SETTINGS_LOAD_ERROR, splitTimeout, UNITS, type TimeoutUnit } from './labels'
import { useReprimandSettings, useSetReprimandSettings } from './queries'

const UNIT_ITEMS = (Object.keys(UNITS) as TimeoutUnit[]).map((unit) => ({ value: unit, label: UNITS[unit].label }))

/** Admins only: who may reprimand besides the admins, and how long the timeout of a reprimand lasts. */
export function ReprimandSettingsCard() {
  const settings = useReprimandSettings()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Rügen</CardTitle>
        <CardDescription>
          Mit /reprimand rügen Admins und Mitglieder der gewählten Rollen andere Mitglieder. Jede Rüge bringt den
          Timeout unten. Alle Rügen stehen unter{' '}
          <Link to="/admin/reprimands" className="underline underline-offset-4">
            Rügen
          </Link>
          .
        </CardDescription>
      </CardHeader>
      <CardContent>
        {settings.isPending ? (
          <LoadingState label="Einstellungen werden geladen …" />
        ) : settings.isError ? (
          <ErrorState message={SETTINGS_LOAD_ERROR} onRetry={() => settings.refetch()} />
        ) : (
          // Keyed so a reload of the saved settings resets the form.
          <ReprimandSettingsForm
            key={`${settings.data.roleIds.join()}|${settings.data.timeoutMinutes}`}
            saved={settings.data}
          />
        )}
      </CardContent>
    </Card>
  )
}

function ReprimandSettingsForm({ saved }: { saved: ReprimandSettings }) {
  const [selected, setSelected] = useState(() => new Set(saved.roleIds))
  const [amount, setAmount] = useState(() => String(splitTimeout(saved.timeoutMinutes).amount))
  const [unit, setUnit] = useState<TimeoutUnit>(() => splitTimeout(saved.timeoutMinutes).unit)
  const setSettings = useSetReprimandSettings()

  const parsed = Number(amount)
  const timeoutMinutes = Number.isInteger(parsed) ? parsed * UNITS[unit].minutes : NaN
  const timeoutValid = timeoutMinutes >= 1 && timeoutMinutes <= MAX_TIMEOUT_MINUTES
  const rolesChanged = selected.size !== saved.roleIds.length || saved.roleIds.some((id) => !selected.has(id))
  const changed = rolesChanged || timeoutMinutes !== saved.timeoutMinutes

  function toggle(id: string, checked: boolean) {
    const next = new Set(selected)
    if (checked) next.add(id)
    else next.delete(id)
    setSelected(next)
  }

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault()
        if (timeoutValid) setSettings.mutate({ roleIds: [...selected], timeoutMinutes })
      }}
    >
      <FieldSet>
        <FieldLegend variant="label">Wer rügen darf</FieldLegend>
        <FieldDescription>
          Admins dürfen immer. Wer rügen darf, kann selbst nicht gerügt werden, ebenso wenig wie Admins und Bots.
        </FieldDescription>
        <FieldGroup data-slot="checkbox-group" className="max-h-72 gap-2 overflow-y-auto">
          {saved.roles.map((role) => (
            <Field key={role.id} orientation="horizontal">
              <Checkbox
                id={`reprimand-role-${role.id}`}
                checked={selected.has(role.id)}
                onCheckedChange={(checked) => toggle(role.id, checked)}
              />
              <FieldLabel htmlFor={`reprimand-role-${role.id}`} className="font-normal">
                <RoleName name={role.name} color={role.color} />
              </FieldLabel>
            </Field>
          ))}
        </FieldGroup>
      </FieldSet>
      <FieldSet className="sm:max-w-sm">
        <FieldLegend variant="label">Timeout pro Rüge</FieldLegend>
        <div className="grid grid-cols-2 gap-2">
          <Field>
            <FieldLabel htmlFor="reprimand-timeout" className="sr-only">
              Dauer
            </FieldLabel>
            <Input
              id="reprimand-timeout"
              type="number"
              min={1}
              value={amount}
              aria-invalid={!timeoutValid}
              aria-describedby="reprimand-timeout-hint"
              onChange={(event) => setAmount(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="reprimand-timeout-unit" className="sr-only">
              Einheit
            </FieldLabel>
            <Select items={UNIT_ITEMS} value={unit} onValueChange={(value) => value && setUnit(value)}>
              <SelectTrigger id="reprimand-timeout-unit" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {UNIT_ITEMS.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </div>
        {timeoutValid ? (
          <FieldDescription id="reprimand-timeout-hint">
            Jede Rüge setzt den Timeout neu auf die volle Dauer, von 1 Minute bis 28 Tage.
          </FieldDescription>
        ) : (
          <FieldError id="reprimand-timeout-hint">Der Timeout muss zwischen 1 Minute und 28 Tagen liegen.</FieldError>
        )}
      </FieldSet>
      <Button type="submit" className="self-start" disabled={!changed || !timeoutValid || setSettings.isPending}>
        Speichern
      </Button>
    </form>
  )
}
