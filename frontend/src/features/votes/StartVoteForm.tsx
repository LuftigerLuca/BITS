import { useState, type FormEvent } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
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
    question.trim() !== '' &&
    roleId !== null &&
    channelId !== null &&
    (end === null || !Number.isNaN(end)) &&
    percentValid

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
          dürfen alle mit der Rolle und einem verknüpften WarEra-Account; das Ergebnis erscheint nach Partei ausgewertet
          im selben Kanal.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {options.isPending ? (
          <LoadingState label="Rollen und Kanäle werden geladen …" />
        ) : options.isError ? (
          <ErrorState message={errorMessage(options.error)} onRetry={() => options.refetch()} />
        ) : (
          <form className="flex flex-col gap-4" onSubmit={submit}>
            <Field>
              <FieldLabel htmlFor="vote-question">Frage</FieldLabel>
              <Input
                id="vote-question"
                value={question}
                maxLength={MAX_QUESTION_LENGTH}
                onChange={(event) => setQuestion(event.target.value)}
              />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="vote-role">Stimmberechtigte Rolle</FieldLabel>
                <SearchableSelect
                  id="vote-role"
                  className="w-full"
                  items={roleItems}
                  value={roleId}
                  onValueChange={setRoleId}
                  placeholder="Rolle wählen"
                  renderItem={(item) => <RoleName name={item.label} color={colors.get(item.value) ?? null} />}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="vote-channel">Kanal</FieldLabel>
                <SearchableSelect
                  id="vote-channel"
                  className="w-full"
                  items={channelItems}
                  value={channelId}
                  onValueChange={setChannelId}
                  placeholder="Kanal wählen"
                  aria-describedby="vote-channel-hint"
                />
                <FieldDescription id="vote-channel-hint">Nur Kanäle, in denen der Bot posten darf.</FieldDescription>
              </Field>
              <Field>
                <FieldLabel htmlFor="vote-end">Ende</FieldLabel>
                <Input
                  id="vote-end"
                  type="datetime-local"
                  value={endsAt}
                  disabled={openEnd}
                  onChange={(event) => setEndsAt(event.target.value)}
                />
                <Field orientation="horizontal">
                  <Checkbox id="vote-open-end" checked={openEnd} onCheckedChange={(checked) => setOpenEnd(checked)} />
                  <FieldLabel htmlFor="vote-open-end" className="font-normal">
                    Ohne Enddatum, bis jemand sie beendet
                  </FieldLabel>
                </Field>
              </Field>
              <Field>
                <div className="grid grid-cols-2 gap-2">
                  <Field>
                    <FieldLabel htmlFor="vote-threshold">Automatisch beenden bei mehr als (%)</FieldLabel>
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
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="vote-threshold-choice">mit der Stimme</FieldLabel>
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
                  </Field>
                </div>
                <FieldDescription id="vote-threshold-hint">
                  Optional, 1 bis 99. Sie endet, sobald mehr als so viel Prozent aller Stimmberechtigten mit dieser
                  Stimme gestimmt haben oder das Ende erreicht ist, je nachdem, was zuerst eintritt; 50 heißt mehr als
                  die Hälfte.
                </FieldDescription>
              </Field>
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
