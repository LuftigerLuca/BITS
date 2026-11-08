import { useState, type FormEvent } from 'react'
import { EmptyState, ErrorState, LoadingState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import type { TopForum, TopSettings } from './api'
import { LOAD_ERROR } from './labels'
import { useSetTopForums, useTopSettings } from './queries'

/** The select has no empty value, so "no tag" gets one of its own. */
const NO_TAG = 'NONE'

function forumLabel(forum: TopForum): string {
  return `#${forum.name}${forum.category ? ` (${forum.category})` : ''}`
}

/** Admins only: the forums whose posts are TOPs, and the tag a closed TOP gets in each. */
export function TopForumsCard() {
  const settings = useTopSettings()

  return (
    <Card>
      <CardHeader>
        <CardTitle>TOP-Foren</CardTitle>
        <CardDescription>
          In den Posts dieser Foren schließen Admins und die Rollen, die Abstimmungen verwalten dürfen, Tagesordnungspunkte
          mit /topclose und öffnen sie mit /topopen wieder.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {settings.isPending ? (
          <LoadingState label="Foren werden geladen …" />
        ) : settings.isError ? (
          <ErrorState message={LOAD_ERROR} onRetry={() => settings.refetch()} />
        ) : (
          // Keyed so a reload of the saved setting resets the form.
          <TopForumsForm
            key={settings.data.selected.map((it) => `${it.forumId}:${it.doneTagId}`).join()}
            settings={settings.data}
          />
        )}
      </CardContent>
    </Card>
  )
}

function TopForumsForm({ settings }: { settings: TopSettings }) {
  const savedTags = new Map(settings.selected.map((it) => [it.forumId, it.doneTagId]))
  // A tag deleted in Discord counts as none, so saving removes it.
  const [selected, setSelected] = useState(
    () =>
      new Map(
        settings.selected.map(({ forumId, doneTagId }) => {
          const tags = settings.forums.find((forum) => forum.id === forumId)?.tags ?? []
          return [forumId, tags.some((tag) => tag.id === doneTagId) ? doneTagId : null] as const
        }),
      ),
  )
  const setForums = useSetTopForums()
  // A forum the bot lost its permissions in stays visible while it is picked, so it can be removed.
  const forums = settings.forums.filter((forum) => forum.usable || savedTags.has(forum.id))
  const changed =
    selected.size !== savedTags.size || [...selected].some(([id, tag]) => !savedTags.has(id) || savedTags.get(id) !== tag)

  function toggle(id: string, checked: boolean) {
    const next = new Map(selected)
    if (checked) next.set(id, null)
    else next.delete(id)
    setSelected(next)
  }

  function setTag(id: string, tag: string | null) {
    setSelected(new Map(selected).set(id, tag))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    setForums.mutate([...selected].map(([forumId, doneTagId]) => ({ forumId, doneTagId })))
  }

  if (forums.length === 0) {
    return (
      <EmptyState
        title="Keine Foren verfügbar"
        description="Lege in Discord ein Forum an und gib dem Bot dort die Rechte, Threads zu verwalten, in Threads Nachrichten zu senden und Links einzubetten."
      />
    )
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      <FieldSet>
        <FieldLegend variant="label">Foren</FieldLegend>
        <FieldDescription>
          Wählbar sind Foren, in denen der Bot Threads verwalten, in Threads Nachrichten senden und Links einbetten darf.
        </FieldDescription>
        <FieldGroup data-slot="checkbox-group" className="gap-2">
          {forums.map((forum) => (
            <Field key={forum.id} orientation="horizontal">
              <Checkbox
                id={`top-forum-${forum.id}`}
                checked={selected.has(forum.id)}
                onCheckedChange={(checked) => toggle(forum.id, checked)}
              />
              <FieldLabel htmlFor={`top-forum-${forum.id}`} className="font-normal">
                {forumLabel(forum)}
                {forum.usable ? '' : ' – Bot fehlen Rechte'}
              </FieldLabel>
            </Field>
          ))}
        </FieldGroup>
      </FieldSet>
      {forums
        .filter((forum) => selected.has(forum.id))
        .map((forum) => {
          const value = selected.get(forum.id) ?? NO_TAG
          return (
            <Field key={forum.id} className="sm:max-w-sm">
              <FieldLabel htmlFor={`top-tag-${forum.id}`}>Erledigt-Tag in #{forum.name}</FieldLabel>
              <SearchableSelect
                id={`top-tag-${forum.id}`}
                className="w-full"
                items={[
                  { value: NO_TAG, label: 'Kein Tag', pinned: true },
                  ...forum.tags.map((it) => ({ value: it.id, label: it.name })),
                ]}
                value={value}
                onValueChange={(next) => setTag(forum.id, next === null || next === NO_TAG ? null : next)}
                placeholder="Tag wählen"
              />
              <FieldDescription>Bekommt ein Post bei /topclose; /topopen nimmt es wieder ab.</FieldDescription>
            </Field>
          )
        })}
      <Button type="submit" className="self-start" disabled={!changed || setForums.isPending}>
        Speichern
      </Button>
    </form>
  )
}
