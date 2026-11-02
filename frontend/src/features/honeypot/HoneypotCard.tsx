import { useState, type FormEvent } from 'react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import type { Honeypot, HoneypotChannel } from './api'
import { LOAD_ERROR } from './labels'
import { useHoneypot, useResumeHoneypotKicks, useSetHoneypotChannel } from './queries'

/** The select has no empty value, so "no honeypot" gets one of its own. */
const NO_CHANNEL = 'NONE'

function channelLabel(channel: HoneypotChannel): string {
  return `#${channel.name}${channel.category ? ` (${channel.category})` : ''}`
}

/** Admins only: the channel in which writing gets a member kicked, and the way out of a pause of the kicks. */
export function HoneypotCard() {
  const honeypot = useHoneypot()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Honeypot</CardTitle>
        <CardDescription>
          Wer in diesen Kanal schreibt, wird automatisch gekickt und verliert die Verknüpfung seines WarEra-Accounts.
          Das fängt Spam-Bots ab; der Bot warnt dort alle anderen.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {honeypot.isPending ? (
          <LoadingState label="Honeypot wird geladen …" />
        ) : honeypot.isError ? (
          <ErrorState message={LOAD_ERROR} onRetry={() => honeypot.refetch()} />
        ) : (
          // Keyed so a reload of the saved channel resets the form.
          <HoneypotForm key={honeypot.data.channelId ?? NO_CHANNEL} saved={honeypot.data} />
        )}
      </CardContent>
    </Card>
  )
}

function HoneypotForm({ saved }: { saved: Honeypot }) {
  const [selected, setSelected] = useState(saved.channelId ?? NO_CHANNEL)
  const [confirming, setConfirming] = useState(false)
  const setChannel = useSetHoneypotChannel()
  const resume = useResumeHoneypotKicks()

  const channelId = selected === NO_CHANNEL ? null : selected
  const items = [
    { value: NO_CHANNEL, label: 'Kein Honeypot', pinned: true },
    ...saved.channels
      .filter((channel) => channel.usable || channel.id === saved.channelId)
      .map((channel) => ({
        value: channel.id,
        label: `${channelLabel(channel)}${channel.usable ? '' : ' – Bot fehlen Rechte'}`,
      })),
  ]
  const chosen = saved.channels.find((channel) => channel.id === channelId)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (channelId === null) setChannel.mutate(null)
    else setConfirming(true)
  }

  return (
    <div className="flex flex-col gap-4">
      {saved.kicksPaused && (
        <div className="flex flex-col items-start gap-2" role="status">
          <Badge variant="destructive">Kicks pausiert</Badge>
          <p className="text-sm text-muted-foreground">
            Der Bot hat 10 Mitglieder in 10 Minuten gekickt und löscht jetzt nur noch Nachrichten. Prüfe im System-Log,
            ob es Bots waren, bevor du fortsetzt.
          </p>
          <Button variant="outline" size="sm" disabled={resume.isPending} onClick={() => resume.mutate()}>
            Kicks fortsetzen
          </Button>
        </div>
      )}
      <form className="flex flex-col gap-4" onSubmit={submit}>
        <Field className="sm:max-w-sm">
          <FieldLabel htmlFor="honeypot-channel">Honeypot-Kanal</FieldLabel>
          <SearchableSelect
            id="honeypot-channel"
            className="w-full"
            items={items}
            value={selected}
            onValueChange={(value) => setSelected(value ?? NO_CHANNEL)}
            placeholder="Kanal wählen"
          />
          <FieldDescription>
            Nimm einen eigenen, leeren Kanal. Der Bot braucht dort die Rechte zum Schreiben, zum Einbetten von Links und zum Verwalten von
            Nachrichten und auf dem Server „Mitglieder kicken“.
          </FieldDescription>
        </Field>
        <Button
          type="submit"
          className="self-start"
          disabled={setChannel.isPending || channelId === saved.channelId}
        >
          Speichern
        </Button>
      </form>
      <ConfirmDialog
        open={confirming}
        title={`#${chosen?.name ?? ''} als Honeypot festlegen?`}
        description="Jede Person, die ab jetzt dort schreibt, wird automatisch gekickt und verliert ihre WarEra-Verknüpfung, auch verifizierte Mitglieder. Wähle nie einen Kanal, der genutzt wird."
        confirm="Als Honeypot festlegen"
        onConfirm={() => {
          setConfirming(false)
          setChannel.mutate(channelId)
        }}
        onCancel={() => setConfirming(false)}
      />
    </div>
  )
}
