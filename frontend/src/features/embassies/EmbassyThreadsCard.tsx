import { useState, type FormEvent } from 'react'
import { PencilLineIcon, SparklesIcon, Trash2Icon, UserPlusIcon } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IconAction } from '@/components/IconAction'
import { EmptyState, ErrorState, LoadingState } from '@/components/QueryState'
import { Subsection } from '@/components/Subsection'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { Embassies, EmbassyCreation, EmbassyThread } from '@/lib/api'
import { useCountries } from '@/lib/countries'
import { errorMessage, MAX_THREAD_NAME_LENGTH, threadName } from './labels'
import {
  useAddMember,
  useAssignThread,
  useCreateThread,
  useDismissCreation,
  useMemberSearch,
  useRemoveMember,
  useRenameThreads,
  useUnassignThread,
} from './queries'

/** The embassy threads by country, who is in each and why, and the form to assign another one. */
export function EmbassyThreadsCard({ embassies }: { embassies: Embassies }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Botschafts-Threads</CardTitle>
        <CardDescription>
          Wähle einen privaten Thread aus Discord oder lass ihn vom Bot im Botschafts-Kanal anlegen. Der Bot braucht im
          Kanal die Rechte „Threads verwalten“ und „Nachrichten in Threads senden“. BITS gleicht die Threads nach jeder
          Verknüpfung und stündlich ab und entfernt alle, die weder ein Amt haben noch manuell hinzugefügt sind, außer
          wer Threads verwalten darf.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {embassies.threads.length > 0 && <RenameButton template={embassies.settings.threadNameTemplate} />}
        {embassies.creations.map((creation) => (
          <CreationSection key={creation.id} creation={creation} />
        ))}
        {embassies.threads.length === 0 && embassies.creations.length === 0 ? (
          <EmptyState
            title="Noch keinem Land ist ein Thread zugeordnet"
            description="Ordne unten einen Thread zu oder lass ihn vom Bot anlegen."
          />
        ) : (
          embassies.threads.map((thread) => <ThreadSection key={thread.threadId} thread={thread} />)
        )}
        <AssignForm embassies={embassies} />
      </CardContent>
    </Card>
  )
}

/** Has the bot rename the assigned threads after the template, once confirmed. */
function RenameButton({ template }: { template: string }) {
  const rename = useRenameThreads()
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <Button variant="outline" size="sm" className="self-start" onClick={() => setConfirming(true)}>
        <PencilLineIcon />
        Bestehende umbenennen
      </Button>
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Bestehende Threads umbenennen?</AlertDialogTitle>
            <AlertDialogDescription>
              Der Bot benennt alle zugeordneten Threads nach der Vorlage „{template}“ um. Archivierte Threads
              überspringt er, Threads mit passendem Namen bleiben unberührt.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <Button
              disabled={rename.isPending}
              onClick={() => rename.mutate(undefined, { onSuccess: () => setConfirming(false) })}
            >
              Umbenennen
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

/** A thread the bot is creating, or why it could not, with the way to try again. */
function CreationSection({ creation }: { creation: EmbassyCreation }) {
  const dismiss = useDismissCreation()
  const [retrying, setRetrying] = useState(false)
  return (
    <Subsection
      title={creation.countryName}
      label={`Botschaft ${creation.countryName}`}
      description={creation.threadName}
      actions={
        creation.failure === null ? (
          <Badge variant="secondary">Wird angelegt …</Badge>
        ) : (
          <>
            <Badge variant="destructive">Anlegen fehlgeschlagen</Badge>
            <Button variant="outline" size="sm" onClick={() => setRetrying(true)}>
              Automatisch anlegen
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={dismiss.isPending}
              onClick={() => dismiss.mutate(creation.id)}
            >
              Verwerfen
            </Button>
          </>
        )
      }
    >
      {creation.failure !== null && <p className="text-sm text-destructive">{creation.failure}</p>}
      {retrying && (
        <CreateDialog
          countryId={creation.countryId}
          countryName={creation.countryName}
          initialName={creation.threadName}
          onClose={() => setRetrying(false)}
        />
      )}
    </Subsection>
  )
}

/** Asks for the name of the thread the bot creates, the one after the template unless changed; mounted while open. */
function CreateDialog({
  countryId,
  countryName,
  initialName,
  onClose,
  onCreated,
}: {
  countryId: string
  countryName: string
  initialName: string
  onClose: () => void
  onCreated?: () => void
}) {
  const create = useCreateThread()
  const [name, setName] = useState(initialName)
  const trimmed = name.trim()
  return (
    <AlertDialog open onOpenChange={(next) => !next && onClose()}>
      <AlertDialogContent>
        <form
          className="contents"
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate(
              { countryId, threadName: trimmed },
              {
                onSuccess: () => {
                  onClose()
                  onCreated?.()
                },
              },
            )
          }}
        >
          <AlertDialogHeader>
            <AlertDialogTitle>Thread für {countryName} anlegen</AlertDialogTitle>
            <AlertDialogDescription>
              Der Bot legt im Botschafts-Kanal einen privaten Thread an und ordnet ihn {countryName} zu.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <Field>
            <FieldLabel htmlFor={`embassy-create-name-${countryId}`}>Name</FieldLabel>
            <Input
              id={`embassy-create-name-${countryId}`}
              value={name}
              maxLength={MAX_THREAD_NAME_LENGTH}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <Button type="submit" disabled={trimmed.length === 0 || create.isPending}>
              Anlegen
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function ThreadSection({ thread }: { thread: EmbassyThread }) {
  const unassign = useUnassignThread()
  const removeMember = useRemoveMember()
  const [unassigning, setUnassigning] = useState(false)
  const [memberToRemove, setMemberToRemove] = useState<EmbassyThread['manualMembers'][number] | null>(null)
  return (
    <Subsection
      title={thread.countryName}
      label={`Botschaft ${thread.countryName}`}
      description={`${thread.channelName ? `#${thread.channelName} › ` : ''}${thread.threadName}`}
      actions={
        <>
          {thread.missing && <Badge variant="destructive">Fehlt in Discord</Badge>}
          {thread.renameSkipped && <Badge variant="outline">Archiviert, beim Umbenennen übersprungen</Badge>}
          <Button variant="outline" size="sm" disabled={unassign.isPending} onClick={() => setUnassigning(true)}>
            Zuordnung aufheben
          </Button>
        </>
      }
    >
      {thread.members.length === 0 ? (
        <p className="text-sm text-muted-foreground">Der Bot hat noch keine Mitglieder gemeldet.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mitglied</TableHead>
              <TableHead>Grund</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {thread.members.map((member) => (
              <TableRow key={member.discordUserId}>
                <TableCell>{member.name}</TableCell>
                <TableCell>{member.reason}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <div className="flex flex-col gap-2">
        <h4 className="text-sm font-medium">Manuell hinzugefügt</h4>
        {thread.manualMembers.length === 0 ? (
          <p className="text-sm text-muted-foreground">Niemand.</p>
        ) : (
          <ul className="flex flex-col gap-1">
            {thread.manualMembers.map((member) => {
              const name = member.name ?? member.discordUserId
              return (
                <li key={member.discordUserId} className="flex items-center gap-2 text-sm">
                  <span>{name}</span>
                  <IconAction
                    label={`${name} aus ${thread.countryName} entfernen`}
                    disabled={removeMember.isPending}
                    onClick={() => setMemberToRemove(member)}
                  >
                    <Trash2Icon />
                  </IconAction>
                </li>
              )
            })}
          </ul>
        )}
        <MemberSearch thread={thread} />
      </div>
      <ConfirmDialog
        open={unassigning}
        title={`Zuordnung von ${thread.countryName} aufheben?`}
        description="Der Thread bleibt in Discord bestehen, BITS verwaltet ihn aber nicht mehr. Wer in keinem anderen Thread ist, verliert die Botschafter-Rolle."
        confirm="Zuordnung aufheben"
        onConfirm={() => {
          setUnassigning(false)
          unassign.mutate(thread.threadId)
        }}
        onCancel={() => setUnassigning(false)}
      />
      <ConfirmDialog
        open={memberToRemove !== null}
        title={`${memberToRemove?.name ?? memberToRemove?.discordUserId ?? 'Mitglied'} aus ${thread.countryName} entfernen?`}
        description="BITS entfernt das Mitglied aus dem Thread. Ist es in keinem anderen Thread, verliert es die Botschafter-Rolle."
        confirm="Entfernen"
        onConfirm={() => {
          if (memberToRemove)
            removeMember.mutate({ threadId: thread.threadId, discordUserId: memberToRemove.discordUserId })
          setMemberToRemove(null)
        }}
        onCancel={() => setMemberToRemove(null)}
      />
    </Subsection>
  )
}

/** Finds a member of the Discord by name or ID and adds them to the thread. */
function MemberSearch({ thread }: { thread: EmbassyThread }) {
  const [query, setQuery] = useState('')
  const search = useMemberSearch(query)
  const addMember = useAddMember()
  const inputId = `embassy-member-${thread.threadId}`
  const added = new Set(thread.manualMembers.map((m) => m.discordUserId))
  return (
    <Field className="sm:max-w-sm">
      <FieldLabel htmlFor={inputId}>Mitglied hinzufügen</FieldLabel>
      <Input id={inputId} value={query} placeholder="Name oder Discord-ID" onChange={(e) => setQuery(e.target.value)} />
      {search.isFetching && <LoadingState label="Mitglieder werden gesucht …" />}
      {search.isError && <ErrorState message={errorMessage(search.error)} onRetry={() => search.refetch()} />}
      {search.data && search.data.length === 0 && <p className="text-sm text-muted-foreground">Keine Treffer.</p>}
      {search.data && search.data.length > 0 && (
        <ul className="flex flex-col gap-1">
          {search.data.map((member) => {
            const name = member.name ?? member.discordUserId
            return (
              <li key={member.discordUserId} className="flex items-center gap-2 text-sm">
                <span className="flex-1 truncate">{name}</span>
                <IconAction
                  label={`${name} zu ${thread.countryName} hinzufügen`}
                  disabled={added.has(member.discordUserId) || addMember.isPending}
                  onClick={() =>
                    addMember.mutate(
                      { threadId: thread.threadId, discordUserId: member.discordUserId },
                      { onSuccess: () => setQuery('') },
                    )
                  }
                >
                  <UserPlusIcon />
                </IconAction>
              </li>
            )
          })}
        </ul>
      )}
    </Field>
  )
}

function AssignForm({ embassies }: { embassies: Embassies }) {
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
