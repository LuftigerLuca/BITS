import { useState, type FormEvent } from 'react'
import { SparklesIcon } from 'lucide-react'
import { ErrorState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Field, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { useCountries } from '@/lib/countries'
import type { Embassies } from './api'
import { CreateDialog } from './CreateDialog'
import { errorMessage, threadName } from './labels'
import { useAssignThread } from './queries'

export function AssignForm({ embassies }: { embassies: Embassies }) {
  const countries = useCountries(true)
  const assign = useAssignThread()
  const [threadId, setThreadId] = useState<string | null>(null)
  const [countryId, setCountryId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  // A country whose creation failed is free again.
  const taken = new Set([
    embassies.ownCountryId,
    ...embassies.threads.map((t) => t.countryId),
    ...embassies.creations.filter((c) => c.failure === null).map((c) => c.countryId),
  ])
  const countryItems = (countries.data ?? [])
    .filter((c) => !taken.has(c.id))
    .map((c) => ({ value: c.id, label: c.name }))
  const countryName = countryItems.find((c) => c.value === countryId)?.label
  const channelSet = embassies.settings.channelId !== null
  const threadItems = embassies.availableThreads.map((t) => ({
    value: t.id,
    label: `#${t.channelName} › ${t.name}${t.archived ? ' (archiviert)' : ''}${t.manageable ? '' : ' – Bot fehlen Rechte'}`,
  }))

  function submit(event: FormEvent) {
    event.preventDefault()
    if (threadId && countryId) {
      assign.mutate(
        { threadId, countryId },
        {
          onSuccess: () => {
            setThreadId(null)
            setCountryId(null)
          },
        },
      )
    }
  }

  return (
    <>
      <form className="flex flex-col gap-4 border-t pt-4" onSubmit={submit}>
        <h3 className="font-heading font-semibold">Thread zuordnen</h3>
        {countries.isError && (
          <ErrorState message={errorMessage(countries.error)} onRetry={() => countries.refetch()} />
        )}
        <div className="flex flex-col gap-4 sm:flex-row">
          <Field className="flex-1">
            <FieldLabel htmlFor="embassy-thread">Thread</FieldLabel>
            <SearchableSelect
              id="embassy-thread"
              className="w-full"
              items={threadItems}
              value={threadId}
              onValueChange={setThreadId}
              placeholder={threadItems.length === 0 ? 'Keine freien privaten Threads' : 'Thread wählen'}
            />
          </Field>
          <Field className="flex-1">
            <FieldLabel htmlFor="embassy-country">Land</FieldLabel>
            <SearchableSelect
              id="embassy-country"
              className="w-full"
              items={countryItems}
              value={countryId}
              onValueChange={setCountryId}
              disabled={countries.isPending}
              placeholder={countries.isPending ? 'Länder werden geladen …' : 'Land wählen'}
            />
          </Field>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button type="submit" disabled={!threadId || !countryId || assign.isPending}>
            Zuordnen
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={!countryId || !channelSet}
            onClick={() => setCreating(true)}
          >
            <SparklesIcon />
            Automatisch anlegen
          </Button>
        </div>
        {!channelSet && (
          <p className="text-sm text-muted-foreground">
            Zum automatischen Anlegen wähle unter „Einstellungen“ einen Botschafts-Kanal.
          </p>
        )}
      </form>
      {/* Outside the form, since a submit in the dialog would bubble up to it through the portal. */}
      {creating && countryId && countryName && (
        <CreateDialog
          countryId={countryId}
          countryName={countryName}
          initialName={threadName(embassies.settings.threadNameTemplate, countryName)}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setThreadId(null)
            setCountryId(null)
          }}
        />
      )}
    </>
  )
}
