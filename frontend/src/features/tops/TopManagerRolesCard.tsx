import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import type { TopRole } from './api'
import { ROLES_LOAD_ERROR } from './labels'
import { useSetTopManagerRoles, useTopManagerRoles } from './queries'

/** Admins only: the roles whose members may close and reopen TOPs besides the admins. */
export function TopManagerRolesCard() {
  const managerRoles = useTopManagerRoles()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Wer TOPs verwalten darf</CardTitle>
        <CardDescription>
          Admins dürfen immer. Mitglieder der gewählten Rollen dürfen TOPs in Discord mit /topclose schließen und mit
          /topopen wieder öffnen, unabhängig davon, wer Abstimmungen verwalten darf.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {managerRoles.isPending ? (
          <LoadingState label="Rollen werden geladen …" />
        ) : managerRoles.isError ? (
          <ErrorState message={ROLES_LOAD_ERROR} onRetry={() => managerRoles.refetch()} />
        ) : (
          // Keyed so a reload of the saved roles resets the selection.
          <TopManagerRolesForm
            key={managerRoles.data.roleIds.join()}
            roles={managerRoles.data.roles}
            saved={managerRoles.data.roleIds}
          />
        )}
      </CardContent>
    </Card>
  )
}

function TopManagerRolesForm({ roles, saved }: { roles: TopRole[]; saved: string[] }) {
  const [selected, setSelected] = useState(() => new Set(saved))
  const setManagerRoles = useSetTopManagerRoles()
  const changed = selected.size !== saved.length || saved.some((id) => !selected.has(id))

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
        setManagerRoles.mutate([...selected])
      }}
    >
      <FieldSet>
        <FieldLegend className="sr-only">Rollen</FieldLegend>
        <FieldGroup data-slot="checkbox-group" className="max-h-72 gap-2 overflow-y-auto">
          {roles.map((role) => (
            <Field key={role.id} orientation="horizontal">
              <Checkbox
                id={`top-manager-${role.id}`}
                checked={selected.has(role.id)}
                onCheckedChange={(checked) => toggle(role.id, checked)}
              />
              <FieldLabel htmlFor={`top-manager-${role.id}`} className="font-normal">
                <RoleName name={role.name} color={role.color} />
              </FieldLabel>
            </Field>
          ))}
        </FieldGroup>
      </FieldSet>
      <Button type="submit" className="self-start" disabled={!changed || setManagerRoles.isPending}>
        Speichern
      </Button>
    </form>
  )
}
