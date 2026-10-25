import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { RoleName } from '@/features/role-sync/RoleName'
import type { VoteOptions } from '@/lib/api'
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
      {setManagerRoles.isError && (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(setManagerRoles.error)}</AlertDescription>
        </Alert>
      )}
      <fieldset className="flex max-h-72 flex-col gap-2 overflow-y-auto">
        <legend className="sr-only">Rollen</legend>
        {roles.map((role) => (
          <label key={role.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-primary"
              checked={selected.has(role.id)}
              onChange={(event) => toggle(role.id, event.target.checked)}
            />
            <RoleName name={role.name} color={role.color} />
          </label>
        ))}
      </fieldset>
      <Button type="submit" className="self-start" disabled={!changed || setManagerRoles.isPending}>
        Speichern
      </Button>
    </form>
  )
}
