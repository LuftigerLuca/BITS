import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { FieldGroup, FieldLabel, FieldLegend, FieldSet, Field } from '@/components/ui/field'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { VoteOptions } from './api'
import { errorMessage } from './labels'
import { useSetVoteManagerRoles, useVoteManagerRoles, useVoteOptions } from './queries'

/** Admins only: the roles whose members may start, end and cancel votes besides the admins. */
export function ManagerRolesCard() {
  const options = useVoteOptions()
  const managerRoles = useVoteManagerRoles()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Wer Abstimmungen verwalten darf</CardTitle>
        <CardDescription>
          Admins dürfen immer. Mitglieder der gewählten Rollen dürfen Abstimmungen über Discord und das Dashboard
          starten, beenden und abbrechen. Im Dashboard gelten neue Rollen erst nach dem nächsten Login.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {options.isPending || managerRoles.isPending ? (
          <LoadingState label="Rollen werden geladen …" />
        ) : options.isError ? (
          <ErrorState message={errorMessage(options.error)} onRetry={() => options.refetch()} />
        ) : managerRoles.isError ? (
          <ErrorState message={errorMessage(managerRoles.error)} onRetry={() => managerRoles.refetch()} />
        ) : (
          // Keyed so a reload of the saved roles resets the selection.
          <ManagerRolesForm key={managerRoles.data.join()} roles={options.data.roles} saved={managerRoles.data} />
        )}
      </CardContent>
    </Card>
  )
}

function ManagerRolesForm({ roles, saved }: { roles: VoteOptions['roles']; saved: string[] }) {
  const [selected, setSelected] = useState(() => new Set(saved))
  const setManagerRoles = useSetVoteManagerRoles()
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
                id={`vote-manager-${role.id}`}
                checked={selected.has(role.id)}
                onCheckedChange={(checked) => toggle(role.id, checked)}
              />
              <FieldLabel htmlFor={`vote-manager-${role.id}`} className="font-normal">
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
