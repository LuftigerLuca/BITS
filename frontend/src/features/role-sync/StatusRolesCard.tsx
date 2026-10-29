import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import type { DiscordRole, StatusRole, StatusRoles } from '@/lib/api'
import { STATUS, errorMessage } from './labels'
import { useRoles, useSetStatusRoles, useStatusRoles } from './queries'

/** Stands for "no role", since a select item needs a value. */
const NONE = 'none'

/** The roles every verified and every unverified member gets, so admins can open channels by the status. */
export function StatusRolesCard() {
  const statusRoles = useStatusRoles()
  const roles = useRoles()
  return (
    <Card>
      <CardHeader>
        <CardTitle>Statusrollen</CardTitle>
        <CardDescription>
          Alle verknüpften Mitglieder bekommen die Rolle für Verifizierte, alle anderen die für Unverifizierte, neue
          Mitglieder direkt beim Beitritt. Bots bekommen keine. So kannst du Kanäle für @everyone sperren und nur mit
          der Rolle für Verifizierte öffnen.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <FeatureOffNotice feature="VERIFICATION">
          Die Verifizierung ist ausgeschaltet: BITS ändert an den Statusrollen nichts, bis sie unter „Verwaltung“ wieder
          eingeschaltet ist.
        </FeatureOffNotice>
        {statusRoles.isPending || roles.isPending ? (
          <LoadingState label="Statusrollen werden geladen …" />
        ) : statusRoles.isError ? (
          <ErrorState message={errorMessage(statusRoles.error)} onRetry={() => statusRoles.refetch()} />
        ) : roles.isError ? (
          <ErrorState message={errorMessage(roles.error)} onRetry={() => roles.refetch()} />
        ) : (
          // Keyed so a reload of the saved roles resets the form.
          <StatusRolesForm key={JSON.stringify(statusRoles.data)} saved={statusRoles.data} roles={roles.data} />
        )}
      </CardContent>
    </Card>
  )
}

function StatusRolesForm({ saved, roles }: { saved: StatusRoles; roles: DiscordRole[] }) {
  const [verifiedRoleId, setVerifiedRoleId] = useState(saved.verified?.roleId ?? null)
  const [unverifiedRoleId, setUnverifiedRoleId] = useState(saved.unverified?.roleId ?? null)
  const setStatusRoles = useSetStatusRoles()
  const changed =
    verifiedRoleId !== (saved.verified?.roleId ?? null) || unverifiedRoleId !== (saved.unverified?.roleId ?? null)
  // A role BITS no longer gives stays with everybody who has it.
  const replaced = [
    saved.verified?.roleId !== verifiedRoleId ? saved.verified : null,
    saved.unverified?.roleId !== unverifiedRoleId ? saved.unverified : null,
  ].filter((role) => role !== null)

  return (
    <form
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault()
        setStatusRoles.mutate({ verifiedRoleId, unverifiedRoleId })
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <StatusRoleSelect
          id="status-role-verified"
          label="Rolle für Verifizierte"
          roles={roles}
          saved={saved.verified}
          other={unverifiedRoleId}
          value={verifiedRoleId}
          onValueChange={setVerifiedRoleId}
        />
        <StatusRoleSelect
          id="status-role-unverified"
          label="Rolle für Unverifizierte"
          roles={roles}
          saved={saved.unverified}
          other={verifiedRoleId}
          value={unverifiedRoleId}
          onValueChange={setUnverifiedRoleId}
        />
      </div>
      {replaced.length > 0 && (
        <Alert>
          <AlertDescription>
            BITS entfernt {replaced.map((role) => `@${role.roleName ?? 'Gelöschte Rolle'}`).join(' und ')} nicht: Wer
            die Rolle hat, behält sie. Entferne sie bei Bedarf in Discord.
          </AlertDescription>
        </Alert>
      )}
      <Button type="submit" className="self-start" disabled={!changed || setStatusRoles.isPending}>
        Speichern
      </Button>
    </form>
  )
}

function StatusRoleSelect({
  id,
  label,
  roles,
  saved,
  other,
  value,
  onValueChange,
}: {
  id: string
  label: string
  roles: DiscordRole[]
  saved: StatusRole | null
  /** The role picked for the other status, which cannot be picked here too. */
  other: string | null
  value: string | null
  onValueChange: (roleId: string | null) => void
}) {
  // Roles with a rule or of a DSK get them by other means.
  const free = roles.filter((r) => r.id === saved?.roleId || (!r.managed && r.dskName === null && r.id !== other))
  const items = [
    { value: NONE, label: 'Keine', pinned: true },
    ...free.map((r) => ({ value: r.id, label: r.name })),
    // The saved role may be gone in Discord or no longer assignable, but stays selectable until it is replaced.
    ...(saved && !free.some((r) => r.id === saved.roleId)
      ? [{ value: saved.roleId, label: saved.roleName ?? 'Gelöschte Rolle' }]
      : []),
  ]
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <SearchableSelect
        id={id}
        className="w-full"
        items={items}
        value={value ?? NONE}
        onValueChange={(next) => onValueChange(next === NONE ? null : next)}
      />
      {saved && saved.roleId === value && saved.status !== 'ACTIVE' && (
        <div>
          <Badge variant={STATUS[saved.status].variant}>{STATUS[saved.status].label}</Badge>
        </div>
      )}
    </div>
  )
}
