import { useState } from 'react'
import { Trash2Icon, UserPlusIcon } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IconAction } from '@/components/IconAction'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Subsection } from '@/components/Subsection'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { EmbassyThread } from './api'
import { errorMessage } from './labels'
import { useAddMember, useMemberSearch, useRemoveMember, useUnassignThread } from './queries'

export function ThreadSection({ thread }: { thread: EmbassyThread }) {
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
