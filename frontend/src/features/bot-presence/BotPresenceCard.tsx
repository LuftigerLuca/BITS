import { useState, type FormEvent } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { MAX_ACTIVITY_TEXT_LENGTH, type BotActivityType, type BotPresence, type BotStatus } from '@/lib/api'
import { ACTIVITY, LOAD_ERROR, STATUS } from './labels'
import { useBotPresence, useSetBotPresence } from './queries'

/** The select has no empty value, so "no activity" gets one of its own. */
const NO_ACTIVITY = 'NONE'

/** Admins only: how the bot shows up in Discord's member list. */
export function BotPresenceCard() {
  const presence = useBotPresence()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Bot-Status</CardTitle>
        <CardDescription>So zeigt Discord den Bot in der Mitgliederliste.</CardDescription>
      </CardHeader>
      <CardContent>
        {presence.isPending ? (
          <LoadingState label="Bot-Status wird geladen …" />
        ) : presence.isError ? (
          <ErrorState message={LOAD_ERROR} onRetry={() => presence.refetch()} />
        ) : (
          // Keyed so a reload of the saved presence resets the form.
          <BotPresenceForm key={JSON.stringify(presence.data)} saved={presence.data} />
        )}
      </CardContent>
    </Card>
  )
}

function BotPresenceForm({ saved }: { saved: BotPresence }) {
  const [status, setStatus] = useState<BotStatus>(saved.status)
  const [type, setType] = useState<BotActivityType | typeof NO_ACTIVITY>(saved.activity?.type ?? NO_ACTIVITY)
  const [text, setText] = useState(saved.activity?.text ?? '')
  const setPresence = useSetBotPresence()

  const activity = type === NO_ACTIVITY ? null : { type, text: text.trim() }
  const changed = JSON.stringify({ status, activity }) !== JSON.stringify(saved)
  const valid = activity === null || activity.text.length > 0

  function submit(event: FormEvent) {
    event.preventDefault()
    setPresence.mutate({ status, activity })
  }

  const statusItems = Object.entries(STATUS).map(([value, label]) => ({ value, label }))
  const activityItems = [
    { value: NO_ACTIVITY, label: 'Keine' },
    ...Object.entries(ACTIVITY).map(([value, label]) => ({ value, label })),
  ]
  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      <div className="flex flex-col gap-1.5 sm:max-w-sm">
        <Label htmlFor="bot-status">Status</Label>
        <Select items={statusItems} value={status} onValueChange={(value) => setStatus(value as BotStatus)}>
          <SelectTrigger id="bot-status" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {statusItems.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex flex-col gap-1.5 sm:max-w-sm">
        <Label htmlFor="bot-activity">Aktivität</Label>
        <Select
          items={activityItems}
          value={type}
          onValueChange={(value) => setType(value as BotActivityType | typeof NO_ACTIVITY)}
        >
          <SelectTrigger id="bot-activity" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {activityItems.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {type !== NO_ACTIVITY && (
        <div className="flex flex-col gap-1.5 sm:max-w-sm">
          <Label htmlFor="bot-activity-text">{type === 'CUSTOM' ? 'Status-Text' : 'Text der Aktivität'}</Label>
          <Input
            id="bot-activity-text"
            value={text}
            maxLength={MAX_ACTIVITY_TEXT_LENGTH}
            placeholder={type === 'CUSTOM' ? 'z. B. Wartung bis 18 Uhr' : 'z. B. WarEra'}
            onChange={(event) => setText(event.target.value)}
          />
        </div>
      )}
      <Button type="submit" className="self-start" disabled={setPresence.isPending || !changed || !valid}>
        Speichern
      </Button>
    </form>
  )
}
