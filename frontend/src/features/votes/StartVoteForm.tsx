import { useState, type FormEvent } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DEFAULT_DURATION_MILLIS, errorMessage, toLocalInput } from './labels'
import { useCreateVote, useVoteOptions } from './queries'

/** Like the backend's limit. */
const MAX_QUESTION_LENGTH = 500

const CHANNEL_CONFLICT =
  'Der Bot kann in diesem Kanal nicht posten. Prüfe seine Rechte dort und versuche es in ein paar Sekunden noch einmal.'

/** Starts a vote; the bot posts it in the channel and pings the role. */
export function StartVoteForm() {
  const options = useVoteOptions()
  const createVote = useCreateVote()
  const [question, setQuestion] = useState('')
  const [roleId, setRoleId] = useState<string | null>(null)
  const [channelId, setChannelId] = useState<string | null>(null)
  const [openEnd, setOpenEnd] = useState(false)
  const [endsAt, setEndsAt] = useState(() => toLocalInput(Date.now() + DEFAULT_DURATION_MILLIS))
  /** Empty for a vote that does not end by participation. */
  const [percent, setPercent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [started, setStarted] = useState(false)

  const end = openEnd ? null : new Date(endsAt).getTime()
  // An end in the past is left to the backend, which knows the time of the request.
  const autoClosePercent = percent.trim() === '' ? null : Number(percent)
  const percentValid = autoClosePercent === null || (Number.isInteger(autoClosePercent) && autoClosePercent >= 1 && autoClosePercent <= 100)
  const valid =
    question.trim() !== '' && roleId !== null && channelId !== null && (end === null || !Number.isNaN(end)) && percentValid

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!valid) return
    setError(null)
    setStarted(false)
    createVote.mutate(
      { question: question.trim(), roleId, channelId, endsAt: end, autoClosePercent },
      {
        onSuccess: () => {
          setQuestion('')
          setStarted(true)
        },
        onError: (e) => setError(errorMessage(e, CHANNEL_CONFLICT)),
      },
    )
  }

  const roleItems = (options.data?.roles ?? []).map((r) => ({ value: r.id, label: r.name }))
  const channelItems = (options.data?.channels ?? []).map((c) => ({
    value: c.id,
    label: c.category ? `#${c.name} (${c.category})` : `#${c.name}`,
  }))

  return (
    <Card>
      <CardHeader>
        <CardTitle>Neue Abstimmung</CardTitle>
        <CardDescription>
          Der Bot postet die Frage mit den Buttons Ja, Nein und Enthaltung im Kanal und pingt die Rolle. Abstimmen
          dürfen alle mit der Rolle und einem verknüpften WarEra-Account; das Ergebnis erscheint nach Partei
          ausgewertet im selben Kanal.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {options.isPending ? (
          <LoadingState label="Rollen und Kanäle werden geladen …" />
        ) : options.isError ? (
          <ErrorState message={errorMessage(options.error)} onRetry={() => options.refetch()} />
        ) : (
          <form className="flex flex-col gap-4" onSubmit={submit}>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            {started && (
              <Alert>
                <AlertDescription>Die Abstimmung ist gestartet und erscheint gleich im Kanal.</AlertDescription>
              </Alert>
            )}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="vote-question">Frage</Label>
              <Input
                id="vote-question"
                value={question}
                maxLength={MAX_QUESTION_LENGTH}
                onChange={(event) => setQuestion(event.target.value)}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="vote-role">Stimmberechtigte Rolle</Label>
                <Select items={roleItems} value={roleId} onValueChange={setRoleId}>
                  <SelectTrigger id="vote-role" className="w-full">
                    <SelectValue placeholder="Rolle wählen" />
                  </SelectTrigger>
                  <SelectContent>
                    {options.data.roles.map((r) => (
                      <SelectItem key={r.id} value={r.id}>
                        <RoleName name={r.name} color={r.color} />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="vote-channel">Kanal</Label>
                <Select items={channelItems} value={channelId} onValueChange={setChannelId}>
                  <SelectTrigger id="vote-channel" className="w-full">
                    <SelectValue placeholder="Kanal wählen" />
                  </SelectTrigger>
                  <SelectContent>
                    {channelItems.map((item) => (
                      <SelectItem key={item.value} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">Nur Kanäle, in denen der Bot posten darf.</p>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="vote-end">Ende</Label>
                <Input
                  id="vote-end"
                  type="datetime-local"
                  value={endsAt}
                  disabled={openEnd}
                  onChange={(event) => setEndsAt(event.target.value)}
                />
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={openEnd}
                    onChange={(event) => setOpenEnd(event.target.checked)}
                  />
                  Ohne Enddatum, bis jemand sie beendet
                </label>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="vote-participation">Automatisch beenden bei Beteiligung (%)</Label>
                <Input
                  id="vote-participation"
                  type="number"
                  min={1}
                  max={100}
                  placeholder="z. B. 60"
                  value={percent}
                  aria-invalid={!percentValid}
                  aria-describedby="vote-participation-hint"
                  onChange={(event) => setPercent(event.target.value)}
                />
                <p id="vote-participation-hint" className="text-xs text-muted-foreground">
                  Optional, 1 bis 100. Sie endet, sobald so viele Stimmberechtigte abgestimmt haben oder das Ende
                  erreicht ist, je nachdem, was zuerst eintritt. Enthaltungen zählen mit.
                </p>
              </div>
            </div>
            <Button type="submit" className="self-start" disabled={createVote.isPending || !valid}>
              Abstimmung starten
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  )
}
