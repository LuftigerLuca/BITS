import { useState } from 'react'
import { ChevronRightIcon } from 'lucide-react'
import { EmptyState, ErrorState, LoadingState } from '@/components/QueryState'
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
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { voteResultImageUrl, type Vote } from './api'
import { STATUS, errorMessage, formatDate, formatTime, participation, thresholdText } from './labels'
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
          <EmptyState
            title="Gerade läuft keine Abstimmung"
            description="Starte eine unter „Neue Abstimmung“ oder mit /vote im Discord."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Frage</TableHead>
                <TableHead>Rolle und Kanal</TableHead>
                <TableHead>Ende</TableHead>
                <TableHead>Beteiligung</TableHead>
                <TableHead className="w-0">
                  <span className="sr-only">Aktionen</span>
                </TableHead>
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
                  <TableCell>
                    <p>{vote.endsAt === null ? 'offen' : formatTime(vote.endsAt)}</p>
                    {vote.threshold !== null && (
                      <p className="text-xs text-muted-foreground">
                        Endet automatisch bei {thresholdText(vote.threshold)}
                      </p>
                    )}
                  </TableCell>
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
                        variant="outline"
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
          <EmptyState title="Noch keine Abstimmung beendet" />
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

function closedText(vote: Vote): string {
  if (vote.closedBy) return `Beendet von ${vote.closedBy}`
  if (vote.endedByThreshold && vote.threshold) return `Automatisch beendet bei ${thresholdText(vote.threshold)}`
  return 'Beendet'
}

function ArchivedVote({ vote }: { vote: Vote }) {
  const status = STATUS[vote.status]
  return (
    <Collapsible render={<article aria-label={vote.question} />}>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h3 className="min-w-0 flex-1 basis-64">
          <CollapsibleTrigger
            render={
              <Button
                variant="ghost"
                size="sm"
                className="group -ml-2.5 h-auto min-h-7 justify-start text-left font-medium whitespace-normal"
              />
            }
          >
            <ChevronRightIcon className="transition-transform group-data-[panel-open]:rotate-90" aria-hidden />
            {vote.question}
          </CollapsibleTrigger>
        </h3>
        {vote.closedAt !== null && (
          <span className="text-xs text-muted-foreground">{formatDate(vote.closedAt)}</span>
        )}
        <Badge variant={status.variant}>{status.label}</Badge>
      </div>
      <CollapsibleContent>
        <div className="mt-2 flex flex-col gap-3 pl-6">
          <div className="text-xs text-muted-foreground">
            <p>
              Rolle: {vote.roleName ?? 'Unbekannte Rolle'} · Kanal: #{vote.channelName ?? 'unbekannt'}
            </p>
            <p>
              Gestartet von {vote.startedBy}, {formatTime(vote.startedAt)}
            </p>
            {vote.closedAt !== null && (
              <p>
                {closedText(vote)}, {formatTime(vote.closedAt)}
              </p>
            )}
          </div>
          {vote.status === 'EVALUATED' && vote.resultText !== null && (
            <>
              <p className="text-sm whitespace-pre-wrap">{vote.resultText}</p>
              <img
                src={voteResultImageUrl(vote.id)}
                alt={`Ergebnis als Grafik: ${vote.question}`}
                loading="lazy"
                className="w-full max-w-xl rounded-md border"
              />
            </>
          )}
          {vote.status === 'COUNTING' && (
            <p className="text-sm text-muted-foreground">Die Auswertung läuft und erscheint gleich im Kanal.</p>
          )}
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}
