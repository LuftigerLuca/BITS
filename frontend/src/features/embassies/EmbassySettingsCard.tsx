import { useState } from 'react'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { OFFICES } from '@/features/role-sync/labels'
import type { Embassies, EmbassySettings, GovernmentOffice } from '@/lib/api'
import { CANNOT_VIEW } from './labels'
import { useSetEmbassySettings } from './queries'

const ALL_OFFICES = Object.keys(OFFICES) as GovernmentOffice[]

/** Stands for "no ambassador role" or "no channel", since a select item needs a value. */
const NONE = 'none'

/** Which offices make someone an ambassador, the ambassador role, the host roles and the embassy channel. */
export function EmbassySettingsCard({ embassies }: { embassies: Embassies }) {
  const saved = embassies.settings
  return (
    <Card>
      <CardHeader>
        <CardTitle>Einstellungen</CardTitle>
        <CardDescription>
          Verknüpfte Mitglieder mit einem der gewählten Ämter kommen in den Thread ihres Landes und bekommen die
          Botschafter-Rolle. Mitglieder der Gastgeber-Rollen kommen in jeden Thread, ohne die Botschafter-Rolle. Ist ein
          Botschafts-Kanal gewählt, legt der Bot dort den Thread eines Landes automatisch an, sobald sich jemand mit
          einem der Ämter dieses Landes verknüpft.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {/* Keyed so a reload of the saved settings resets the form. */}
        <SettingsForm key={JSON.stringify(saved)} roles={embassies.roles} channels={embassies.channels} saved={saved} />
      </CardContent>
    </Card>
  )
}

function SettingsForm({
  roles,
  channels,
  saved,
}: {
  roles: Embassies['roles']
  channels: Embassies['channels']
  saved: EmbassySettings
}) {
  const [offices, setOffices] = useState(() => new Set(saved.offices))
  const [ambassadorRoleId, setAmbassadorRoleId] = useState(saved.ambassadorRoleId)
  const [hostRoleIds, setHostRoleIds] = useState(() => new Set(saved.hostRoleIds))
  const [channelId, setChannelId] = useState(saved.channelId)
  const channel = channels.find((c) => c.id === channelId)
  const setSettings = useSetEmbassySettings(
    channel && !channel.threadsCreatable ? CANNOT_VIEW.channel : CANNOT_VIEW.roles,
  )
  // In a fixed order, so the comparison with what is saved does not depend on the order of the clicks.
  const normalize = (
    officeSet: Set<GovernmentOffice>,
    roleId: string | null,
    hostSet: Set<string>,
    channel: string | null,
  ): EmbassySettings => ({
    offices: ALL_OFFICES.filter((office) => officeSet.has(office)),
    ambassadorRoleId: roleId,
    hostRoleIds: roles.filter((role) => hostSet.has(role.id)).map((role) => role.id),
    channelId: channel,
  })
  const settings = normalize(offices, ambassadorRoleId, hostRoleIds, channelId)
  const changed =
    JSON.stringify(settings) !==
    JSON.stringify(
      normalize(new Set(saved.offices), saved.ambassadorRoleId, new Set(saved.hostRoleIds), saved.channelId),
    )

  function toggle<T>(set: Set<T>, value: T, checked: boolean): Set<T> {
    const next = new Set(set)
    if (checked) next.add(value)
    else next.delete(value)
    return next
  }

  const roleItems = [{ value: NONE, label: 'Keine' }, ...roles.map((role) => ({ value: role.id, label: role.name }))]
  const channelItems = [
    { value: NONE, label: 'Keiner' },
    ...channels.map((c) => ({
      value: c.id,
      label: `${c.category ? `${c.category} › ` : ''}#${c.name}${c.threadsCreatable ? '' : ' – Bot fehlen Rechte'}`,
    })),
  ]
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
        <SearchableSelect
          id="embassy-ambassador-role"
          className="w-full"
          items={roleItems}
          value={ambassadorRoleId ?? NONE}
          onValueChange={(value) => setAmbassadorRoleId(value === NONE ? null : value)}
        />
      </div>
      <div className="flex flex-col gap-1.5 sm:max-w-sm">
        <Label htmlFor="embassy-channel">Botschafts-Kanal</Label>
        <SearchableSelect
          id="embassy-channel"
          className="w-full"
          items={channelItems}
          value={channelId ?? NONE}
          onValueChange={(value) => setChannelId(value === NONE ? null : value)}
        />
        <p className="text-sm text-muted-foreground">
          Hier legt der Bot neue Botschafts-Threads an. Er braucht dort „Private Threads erstellen“, „Threads verwalten“
          und „Nachrichten in Threads senden“.
        </p>
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
