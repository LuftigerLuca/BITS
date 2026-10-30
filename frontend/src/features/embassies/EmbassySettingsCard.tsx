import { useState } from 'react'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { OFFICES } from '@/features/role-sync/labels'
import type { Embassies, EmbassySettings, GovernmentOffice } from '@/lib/api'
import { CANNOT_VIEW, MAX_THREAD_NAME_LENGTH, PLACEHOLDER, templateError } from './labels'
import { useSetEmbassySettings } from './queries'

const ALL_OFFICES = Object.keys(OFFICES) as GovernmentOffice[]

/** Stands for "no ambassador role" or "no channel", since a select item needs a value. */
const NONE = 'none'

/**
 * Which offices make someone an ambassador, the ambassador role, the host roles, the embassy channel and the name of
 * the threads.
 */
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
  const [template, setTemplate] = useState(saved.threadNameTemplate)
  const invalidTemplate = templateError(template)
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
    nameTemplate: string,
  ): EmbassySettings => ({
    offices: ALL_OFFICES.filter((office) => officeSet.has(office)),
    ambassadorRoleId: roleId,
    hostRoleIds: roles.filter((role) => hostSet.has(role.id)).map((role) => role.id),
    channelId: channel,
    threadNameTemplate: nameTemplate.trim(),
  })
  const settings = normalize(offices, ambassadorRoleId, hostRoleIds, channelId, template)
  const changed =
    JSON.stringify(settings) !==
    JSON.stringify(
      normalize(
        new Set(saved.offices),
        saved.ambassadorRoleId,
        new Set(saved.hostRoleIds),
        saved.channelId,
        saved.threadNameTemplate,
      ),
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
      <FieldSet>
        <FieldLegend variant="label">Ämter</FieldLegend>
        <FieldGroup data-slot="checkbox-group" className="gap-2">
          {ALL_OFFICES.map((office) => (
            <Field key={office} orientation="horizontal">
              <Checkbox
                id={`embassy-office-${office}`}
                checked={offices.has(office)}
                onCheckedChange={(checked) => setOffices(toggle(offices, office, checked))}
              />
              <FieldLabel htmlFor={`embassy-office-${office}`} className="font-normal">
                {OFFICES[office]}
              </FieldLabel>
            </Field>
          ))}
        </FieldGroup>
      </FieldSet>
      <Field className="sm:max-w-sm">
        <FieldLabel htmlFor="embassy-ambassador-role">Botschafter-Rolle</FieldLabel>
        <SearchableSelect
          id="embassy-ambassador-role"
          className="w-full"
          items={roleItems}
          value={ambassadorRoleId ?? NONE}
          onValueChange={(value) => setAmbassadorRoleId(value === NONE ? null : value)}
        />
      </Field>
      <Field className="sm:max-w-sm">
        <FieldLabel htmlFor="embassy-channel">Botschafts-Kanal</FieldLabel>
        <SearchableSelect
          id="embassy-channel"
          className="w-full"
          items={channelItems}
          value={channelId ?? NONE}
          onValueChange={(value) => setChannelId(value === NONE ? null : value)}
        />
        <FieldDescription>
          Hier legt der Bot neue Botschafts-Threads an. Er braucht dort „Private Threads erstellen“, „Threads verwalten“
          und „Nachrichten in Threads senden“.
        </FieldDescription>
      </Field>
      <Field className="sm:max-w-sm">
        <FieldLabel htmlFor="embassy-thread-name">Thread-Name</FieldLabel>
        <Input
          id="embassy-thread-name"
          value={template}
          maxLength={MAX_THREAD_NAME_LENGTH}
          aria-invalid={invalidTemplate !== null}
          aria-describedby="embassy-thread-name-hint"
          onChange={(event) => setTemplate(event.target.value)}
        />
        {invalidTemplate ? (
          <FieldError id="embassy-thread-name-hint">{invalidTemplate}</FieldError>
        ) : (
          <FieldDescription id="embassy-thread-name-hint">
            So benennt der Bot neue Threads. {PLACEHOLDER} steht für den englischen Ländernamen aus WarEra, z. B.
            „Germany“. Bestehende Threads benennst du unter „Botschafts-Threads“ um.
          </FieldDescription>
        )}
      </Field>
      <FieldSet>
        <FieldLegend variant="label">Gastgeber-Rollen</FieldLegend>
        <FieldGroup data-slot="checkbox-group" className="max-h-72 gap-2 overflow-y-auto">
          {roles.map((role) => (
            <Field key={role.id} orientation="horizontal">
              <Checkbox
                id={`embassy-host-${role.id}`}
                checked={hostRoleIds.has(role.id)}
                onCheckedChange={(checked) => setHostRoleIds(toggle(hostRoleIds, role.id, checked))}
              />
              <FieldLabel htmlFor={`embassy-host-${role.id}`} className="font-normal">
                <RoleName name={role.name} color={role.color} />
              </FieldLabel>
            </Field>
          ))}
        </FieldGroup>
      </FieldSet>
      <Button
        type="submit"
        className="self-start"
        disabled={!changed || invalidTemplate !== null || setSettings.isPending}
      >
        Speichern
      </Button>
    </form>
  )
}
