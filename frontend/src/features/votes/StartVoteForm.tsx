import { useState, type FormEvent } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { ThresholdChoice } from '@/lib/api'
import { DEFAULT_DURATION_MILLIS, THRESHOLD_CHOICE, errorMessage, toLocalInput } from './labels'
import { useCreateVote, useVoteOptions } from './queries'

/** Like the backend's limit. */
const MAX_QUESTION_LENGTH = 500

const THRESHOLD_ITEMS = (['YES', 'NO'] as const).map((value) => ({ value, label: THRESHOLD_CHOICE[value] }))

/** Starts a vote; the bot posts it in the channel and pings the role. */
export function StartVoteForm() {
  const options = useVoteOptions()
  const createVote = useCreateVote()
  const [question, setQuestion] = useState('')
  const [roleId, setRoleId] = useState<string | null>(null)
  const [channelId, setChannelId] = useState<string | null>(null)
  const [openEnd, setOpenEnd] = useState(false)
  const [endsAt, setEndsAt] = useState(() => toLocalInput(Date.now() + DEFAULT_DURATION_MILLIS))
  /** Empty for a vote that does not end by a threshold. */
  const [percent, setPercent] = useState('')
  const [thresholdChoice, setThresholdChoice] = useState<ThresholdChoice>('YES')

  const end = openEnd ? null : new Date(endsAt).getTime()
  // An end in the past is left to the backend, which knows the time of the request.
  const thresholdPercent = percent.trim() === '' ? null : Number(percent)
  const percentValid =
    thresholdPercent === null || (Number.isInteger(thresholdPercent) && thresholdPercent >= 1 && thresholdPercent <= 99)
  const threshold = thresholdPercent === null ? null : { percent: thresholdPercent, choice: thresholdChoice }
  const valid =
    question.trim() !== '' && roleId !== null && channelId !== null && (end === null || !Number.isNaN(end)) && percentValid

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!valid) return
    createVote.mutate(
      { question: question.trim(), roleId, channelId, endsAt: end, threshold },
      { onSuccess: () => setQuestion('') },
    )
  }

  const roleItems = (options.data?.roles ?? []).map((r) => ({ value: r.id, label: r.name }))
  const colors = new Map((options.data?.roles ?? []).map((r) => [r.id, r.color]))
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
                <SearchableSelect
                  id="vote-role"
                  className="w-full"
                  items={roleItems}
                  value={roleId}
                  onValueChange={setRoleId}
                  placeholder="Rolle wählen"
                  renderItem={(item) => <RoleName name={item.label} color={colors.get(item.value) ?? null} />}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="vote-channel">Kanal</Label>
                <SearchableSelect
                  id="vote-channel"
                  className="w-full"
                  items={channelItems}
                  value={channelId}
                  onValueChange={setChannelId}
                  placeholder="Kanal wählen"
                />
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
                <div className="grid grid-cols-2 gap-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="vote-threshold">Automatisch beenden bei mehr als (%)</Label>
                    <Input
                      id="vote-threshold"
                      type="number"
                      min={1}
                      max={99}
                      placeholder="z. B. 50"
                      value={percent}
                      aria-invalid={!percentValid}
                      aria-describedby="vote-threshold-hint"
                      onChange={(event) => setPercent(event.target.value)}
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="vote-threshold-choice">mit der Stimme</Label>
                    <Select
                      items={THRESHOLD_ITEMS}
                      value={thresholdChoice}
                      disabled={thresholdPercent === null}
                      onValueChange={(value) => value && setThresholdChoice(value)}
                    >
                      <SelectTrigger id="vote-threshold-choice" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {THRESHOLD_ITEMS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <p id="vote-threshold-hint" className="text-xs text-muted-foreground">
                  Optional, 1 bis 99. Sie endet, sobald mehr als so viel Prozent aller Stimmberechtigten mit dieser
                  Stimme gestimmt haben oder das Ende erreicht ist, je nachdem, was zuerst eintritt; 50 heißt mehr als
                  die Hälfte.
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
