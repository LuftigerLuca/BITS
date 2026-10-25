import { useState } from 'react'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { OFFICES } from '@/features/role-sync/labels'
import type { Embassies, EmbassySettings, GovernmentOffice } from '@/lib/api'
import { useSetEmbassySettings } from './queries'

const ALL_OFFICES = Object.keys(OFFICES) as GovernmentOffice[]

/** Stands for "no ambassador role", since a select item needs a value. */
const NO_ROLE = 'none'

/** Which offices make someone an ambassador, the ambassador role and the host roles. */
export function EmbassySettingsCard({ embassies }: { embassies: Embassies }) {
  const saved = embassies.settings
  return (
    <Card>
      <CardHeader>
        <CardTitle>Einstellungen</CardTitle>
        <CardDescription>
          Verknüpfte Mitglieder mit einem der gewählten Ämter kommen in den Thread ihres Landes und bekommen die
          Botschafter-Rolle. Mitglieder der Gastgeber-Rollen kommen in jeden Thread, ohne die Botschafter-Rolle.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {/* Keyed so a reload of the saved settings resets the form. */}
        <SettingsForm key={JSON.stringify(saved)} roles={embassies.roles} saved={saved} />
      </CardContent>
    </Card>
  )
}

function SettingsForm({ roles, saved }: { roles: Embassies['roles']; saved: EmbassySettings }) {
  const [offices, setOffices] = useState(() => new Set(saved.offices))
  const [ambassadorRoleId, setAmbassadorRoleId] = useState(saved.ambassadorRoleId)
  const [hostRoleIds, setHostRoleIds] = useState(() => new Set(saved.hostRoleIds))
  const setSettings = useSetEmbassySettings()
  // In a fixed order, so the comparison with what is saved does not depend on the order of the clicks.
  const normalize = (officeSet: Set<GovernmentOffice>, roleId: string | null, hostSet: Set<string>): EmbassySettings => ({
    offices: ALL_OFFICES.filter((office) => officeSet.has(office)),
    ambassadorRoleId: roleId,
    hostRoleIds: roles.filter((role) => hostSet.has(role.id)).map((role) => role.id),
  })
  const settings = normalize(offices, ambassadorRoleId, hostRoleIds)
  const changed =
    JSON.stringify(settings) !==
    JSON.stringify(normalize(new Set(saved.offices), saved.ambassadorRoleId, new Set(saved.hostRoleIds)))

  function toggle<T>(set: Set<T>, value: T, checked: boolean): Set<T> {
    const next = new Set(set)
    if (checked) next.add(value)
    else next.delete(value)
    return next
  }

  const roleItems = [{ value: NO_ROLE, label: 'Keine' }, ...roles.map((role) => ({ value: role.id, label: role.name }))]
  return (
    <form
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault()
        setSettings.mutate(settings)
      }}
    >
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">Ämter</legend>
        {ALL_OFFICES.map((office) => (
          <label key={office} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-primary"
              checked={offices.has(office)}
              onChange={(event) => setOffices(toggle(offices, office, event.target.checked))}
            />
            {OFFICES[office]}
          </label>
        ))}
      </fieldset>
      <div className="flex flex-col gap-1.5 sm:max-w-sm">
        <Label htmlFor="embassy-ambassador-role">Botschafter-Rolle</Label>
        <Select
          items={roleItems}
          value={ambassadorRoleId ?? NO_ROLE}
          onValueChange={(value: string | null) => setAmbassadorRoleId(value === NO_ROLE ? null : value)}
        >
          <SelectTrigger id="embassy-ambassador-role" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {roleItems.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <fieldset className="flex max-h-72 flex-col gap-2 overflow-y-auto">
        <legend className="mb-2 text-sm font-medium">Gastgeber-Rollen</legend>
        {roles.map((role) => (
          <label key={role.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-primary"
              checked={hostRoleIds.has(role.id)}
              onChange={(event) => setHostRoleIds(toggle(hostRoleIds, role.id, event.target.checked))}
            />
            <RoleName name={role.name} color={role.color} />
          </label>
        ))}
      </fieldset>
      <Button type="submit" className="self-start" disabled={!changed || setSettings.isPending}>
        Speichern
      </Button>
    </form>
  )
}
