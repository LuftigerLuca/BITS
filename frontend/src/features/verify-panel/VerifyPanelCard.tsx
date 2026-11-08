import { useState, type FormEvent } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import type { VerifyPanel, VerifyPanelChannel } from './api'
import { LOAD_ERROR } from './labels'
import { useSetVerifyPanelChannel, useVerifyPanel } from './queries'

/** The select has no empty value, so "no channel" gets one of its own. */
const NO_CHANNEL = 'NONE'

function channelLabel(channel: VerifyPanelChannel): string {
  return `#${channel.name}${channel.category ? ` (${channel.category})` : ''}`
}

/** Admins only: the channel in which the bot keeps a message with a button to verify, the way in for new members. */
export function VerifyPanelCard() {
  const panel = useVerifyPanel()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Verifizierungs-Kanal</CardTitle>
        <CardDescription>
          Der Bot postet hier eine Nachricht mit dem Button „Jetzt verifizieren“. Der Button führt neue Mitglieder durch
          dieselbe Verifizierung wie <code>/verify</code>.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {panel.isPending ? (
          <LoadingState label="Verifizierungs-Kanal wird geladen …" />
        ) : panel.isError ? (
          <ErrorState message={LOAD_ERROR} onRetry={() => panel.refetch()} />
        ) : (
          // Keyed so a reload of the saved channel resets the form.
          <VerifyPanelForm key={panel.data.channelId ?? NO_CHANNEL} saved={panel.data} />
        )}
      </CardContent>
    </Card>
  )
}

function VerifyPanelForm({ saved }: { saved: VerifyPanel }) {
  const [selected, setSelected] = useState(saved.channelId ?? NO_CHANNEL)
  const setChannel = useSetVerifyPanelChannel()

  const channelId = selected === NO_CHANNEL ? null : selected
  const savedChannel = saved.channels.find((channel) => channel.id === saved.channelId)
  const items = [
    { value: NO_CHANNEL, label: 'Kein Verifizierungs-Kanal', pinned: true },
    ...saved.channels
      .filter((channel) => channel.usable || channel.id === saved.channelId)
      .map((channel) => ({
        value: channel.id,
        label: `${channelLabel(channel)}${channel.usable ? '' : ' – Bot fehlen Rechte'}`,
      })),
  ]

  function submit(event: FormEvent) {
    event.preventDefault()
    setChannel.mutate(channelId)
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      <Field className="sm:max-w-sm">
        <FieldLabel htmlFor="verify-panel-channel">Kanal für neue Mitglieder</FieldLabel>
        <SearchableSelect
          id="verify-panel-channel"
          className="w-full"
          items={items}
          value={selected}
          onValueChange={(value) => setSelected(value ?? NO_CHANNEL)}
          placeholder="Kanal wählen"
        />
        <FieldDescription>
          Der Bot braucht dort die Rechte zum Lesen, Schreiben, Einbetten von Links, Anhängen von Dateien und zum Lesen
          des Verlaufs. Bei einem Wechsel ersetzt er seine Nachricht im alten Kanal.
        </FieldDescription>
      </Field>
      {savedChannel && !savedChannel.usable && (
        <p className="text-sm text-destructive" role="status">
          Dem Bot fehlen in #{savedChannel.name} Rechte. Er kann seine Nachricht dort nicht aktuell halten.
        </p>
      )}
      <Button type="submit" className="self-start" disabled={setChannel.isPending || channelId === saved.channelId}>
        Speichern
      </Button>
    </form>
  )
}
