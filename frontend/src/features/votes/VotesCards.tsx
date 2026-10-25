import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import {
  AlertDialog,
  AlertDialogAction,
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { voteResultImageUrl, type Vote } from '@/lib/api'
import { STATUS, errorMessage, formatTime, participation } from './labels'
import { useCloseVote, useVotes } from './queries'

/** The running votes with their participation, to end or cancel early. */
export function OpenVotesCard() {
  const votes = useVotes()
  const closeVote = useCloseVote()
  const [closing, setClosing] = useState<{ vote: Vote; cancel: boolean } | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Laufende Abstimmungen</CardTitle>
        <CardDescription>
          Beenden startet die Auswertung, die kurz darauf im Kanal der Abstimmung erscheint. Abbrechen beendet sie ohne
          Ergebnis und löscht die abgegebenen Stimmen.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {votes.isPending ? (
          <LoadingState label="Abstimmungen werden geladen …" />
        ) : votes.isError ? (
          <ErrorState message={errorMessage(votes.error)} onRetry={() => votes.refetch()} />
        ) : votes.data.open.length === 0 ? (
          <p className="text-sm text-muted-foreground">Gerade läuft keine Abstimmung.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Frage</TableHead>
                <TableHead>Rolle und Kanal</TableHead>
                <TableHead>Ende</TableHead>
                <TableHead>Beteiligung</TableHead>
                <TableHead className="w-0" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {votes.data.open.map((vote) => (
                <TableRow key={vote.id}>
                  <TableCell className="max-w-80 whitespace-normal">
                    <p className="font-medium">{vote.question}</p>
                    <p className="text-xs text-muted-foreground">
                      von {vote.startedBy}, {formatTime(vote.startedAt)}
                    </p>
                  </TableCell>
                  <TableCell>
                    <p>{vote.roleName ?? 'Unbekannte Rolle'}</p>
                    <p className="text-xs text-muted-foreground">#{vote.channelName ?? 'unbekannt'}</p>
                  </TableCell>
                  <TableCell>{vote.endsAt === null ? 'offen' : formatTime(vote.endsAt)}</TableCell>
                  <TableCell>{participation(vote.voted, vote.eligible)}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={closeVote.isPending}
                        onClick={() => setClosing({ vote, cancel: false })}
                      >
                        Beenden
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={closeVote.isPending}
                        onClick={() => setClosing({ vote, cancel: true })}
                      >
                        Abbrechen
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <CloseVoteDialog
          closing={closing}
          onConfirm={({ vote, cancel }) => {
            setClosing(null)
            closeVote.mutate({ id: vote.id, cancel })
          }}
          onCancel={() => setClosing(null)}
        />
      </CardContent>
    </Card>
  )
}

/** Ending or cancelling cannot be undone, and votes of members who have not voted yet are lost. */
function CloseVoteDialog({
  closing,
  onConfirm,
  onCancel,
}: {
  closing: { vote: Vote; cancel: boolean } | null
  onConfirm: (closing: { vote: Vote; cancel: boolean }) => void
  onCancel: () => void
}) {
  const cancel = closing?.cancel ?? false
  return (
    <AlertDialog open={closing !== null} onOpenChange={(open) => !open && onCancel()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{cancel ? 'Abstimmung abbrechen?' : 'Abstimmung beenden?'}</AlertDialogTitle>
          <AlertDialogDescription>
            {closing && (
              <>
                <strong>{closing.vote.question}</strong>{' '}
                {cancel
                  ? 'Es gibt keine Auswertung, und die abgegebenen Stimmen werden gelöscht.'
                  : 'Danach wird nicht mehr abgestimmt; die Auswertung erscheint im Kanal der Abstimmung.'}
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Zurück</AlertDialogCancel>
          <AlertDialogAction variant={cancel ? 'destructive' : 'default'} onClick={() => closing && onConfirm(closing)}>
            {cancel ? 'Abstimmung abbrechen' : 'Abstimmung beenden'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

/** Ended votes, newest first, with the result as it was posted in Discord. */
export function VoteArchiveCard() {
  const votes = useVotes()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Archiv</CardTitle>
        <CardDescription>
          Beendete Abstimmungen mit ihrer Auswertung. Wer wie abgestimmt hat, speichert BITS nach der Auswertung nicht.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {votes.isPending ? (
          <LoadingState label="Abstimmungen werden geladen …" />
        ) : votes.isError ? (
          <ErrorState message={errorMessage(votes.error)} onRetry={() => votes.refetch()} />
        ) : votes.data.closed.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine Abstimmung beendet.</p>
        ) : (
          <ul className="flex flex-col divide-y">
            {votes.data.closed.map((vote) => (
              <li key={vote.id} className="py-4 first:pt-0 last:pb-0">
                <ArchivedVote vote={vote} />
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function ArchivedVote({ vote }: { vote: Vote }) {
  const status = STATUS[vote.status]
  return (
    <article className="flex flex-col gap-3" aria-label={vote.question}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="font-medium">{vote.question}</h3>
          <p className="text-xs text-muted-foreground">
            {vote.roleName ?? 'Unbekannte Rolle'} in #{vote.channelName ?? 'unbekannt'} · gestartet von {vote.startedBy},{' '}
            {formatTime(vote.startedAt)}
            {vote.closedAt !== null && (
              <>
                {' '}
                · {vote.closedBy ? `beendet von ${vote.closedBy}` : 'beendet'}, {formatTime(vote.closedAt)}
              </>
            )}
          </p>
        </div>
        <Badge variant={status.variant}>{status.label}</Badge>
      </div>
      {vote.status === 'EVALUATED' && vote.resultText !== null && (
        <details className="group">
          <summary className="cursor-pointer text-sm font-medium">Ergebnis anzeigen</summary>
          <div className="mt-3 flex flex-col gap-3">
            <img
              src={voteResultImageUrl(vote.id)}
              alt={`Ergebnis als Grafik: ${vote.question}`}
              loading="lazy"
              className="w-full max-w-3xl rounded-md border"
            />
            <p className="text-sm whitespace-pre-wrap">{vote.resultText}</p>
          </div>
        </details>
      )}
      {vote.status === 'COUNTING' && (
        <p className="text-sm text-muted-foreground">Die Auswertung läuft und erscheint gleich im Kanal.</p>
      )}
    </article>
  )
}
