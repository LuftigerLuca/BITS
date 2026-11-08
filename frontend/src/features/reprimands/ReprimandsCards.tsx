import { useState } from 'react'
import { Undo2Icon } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IconAction } from '@/components/IconAction'
import { EmptyState } from '@/components/QueryState'
import { Subsection } from '@/components/Subsection'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { Reprimand, ReprimandCount } from './api'
import { formatTime } from './labels'
import { useWithdrawReprimand } from './queries'

/** How many reprimands count per member, the most first. */
export function ReprimandCountsCard({ counts }: { counts: ReprimandCount[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Anzahl pro Mitglied</CardTitle>
        <CardDescription>Rügen verfallen nicht; zurückgenommene zählen nicht mit.</CardDescription>
      </CardHeader>
      <CardContent>
        {counts.length === 0 ? (
          <EmptyState title="Niemand hat eine Rüge" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Mitglied</TableHead>
                <TableHead>Discord-ID</TableHead>
                <TableHead className="text-right">Rügen</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {counts.map((count) => (
                <TableRow key={count.discordUserId}>
                  <TableCell>{count.memberName}</TableCell>
                  <TableCell className="text-muted-foreground">{count.discordUserId}</TableCell>
                  <TableCell className="text-right">{count.count}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

/** Every reprimand, the newest first, each with the way to withdraw it. */
export function ReprimandListCard({ reprimands }: { reprimands: Reprimand[] }) {
  const [withdrawing, setWithdrawing] = useState<Reprimand | null>(null)
  const withdraw = useWithdrawReprimand()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Alle Rügen</CardTitle>
        <CardDescription>
          Eine zurückgenommene Rüge zählt nicht mehr, und ein Timeout, den sie noch hält, wird aufgehoben. Sie bleibt
          hier sichtbar.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {reprimands.length === 0 ? (
          <EmptyState title="Noch keine Rüge" description="Rügen gibt es in Discord mit /reprimand." />
        ) : (
          reprimands.map((reprimand) => (
            <Subsection
              key={reprimand.id}
              title={reprimand.memberName}
              label={`Rüge für ${reprimand.memberName} vom ${formatTime(reprimand.createdAt)}`}
              description={
                <>
                  {formatTime(reprimand.createdAt)} · von {reprimand.actorName}
                  {reprimand.withdrawnAt !== null &&
                    ` · zurückgenommen von ${reprimand.withdrawnBy ?? 'unbekannt'} am ${formatTime(reprimand.withdrawnAt)}`}
                </>
              }
              actions={
                reprimand.withdrawnAt !== null ? (
                  <Badge variant="secondary">Zurückgenommen</Badge>
                ) : (
                  <IconAction
                    label={`Rüge für ${reprimand.memberName} zurücknehmen`}
                    disabled={withdraw.isPending}
                    onClick={() => setWithdrawing(reprimand)}
                  >
                    <Undo2Icon />
                  </IconAction>
                )
              }
            >
              <p className="text-sm break-words">{reprimand.reason}</p>
            </Subsection>
          ))
        )}
      </CardContent>
      <ConfirmDialog
        open={withdrawing !== null}
        title={`Rüge für ${withdrawing?.memberName ?? ''} zurücknehmen?`}
        description="Sie zählt dann nicht mehr. Läuft ihr Timeout noch, hebt der Bot ihn auf, soweit keine andere Rüge ihn hält."
        confirm="Zurücknehmen"
        onConfirm={() => {
          if (withdrawing) withdraw.mutate(withdrawing.id)
          setWithdrawing(null)
        }}
        onCancel={() => setWithdrawing(null)}
      />
    </Card>
  )
}
