import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AuditAction, AuditActor } from '@/lib/api'
import { ACTIONS, ERROR_MESSAGE, formatTime } from './labels'
import { useAuditActors, useAuditLog } from './queries'

/** The Select's value for "no filter"; real values are subjects and action names, which never look like this. */
const ALL = 'all'

/** Who changed what in the dashboard, newest first; entries stay after the rule or role is gone. */
export function AuditLogCard() {
  const [actor, setActor] = useState(ALL)
  const [action, setAction] = useState(ALL)
  const log = useAuditLog({
    actor: actor === ALL ? null : actor,
    action: action === ALL ? null : (action as AuditAction),
  })
  const actors = useAuditActors()

  const actorItems = [
    { value: ALL, label: 'Alle Admins' },
    ...(actors.data ?? []).map((a) => ({ value: a.subject, label: actorLabel(a) })),
  ]
  const actionItems = [
    { value: ALL, label: 'Alle Aktionen' },
    ...Object.entries(ACTIONS).map(([value, label]) => ({ value, label })),
  ]
  const entries = log.data?.pages.flatMap((page) => page.entries) ?? []
  const filtered = actor !== ALL || action !== ALL

  return (
    <Card>
      <CardHeader>
        <CardTitle>Änderungen im Dashboard</CardTitle>
        <CardDescription>
          Wer wann was geändert hat, neueste zuerst. Die Beschreibung zeigt den Stand zum Zeitpunkt der Änderung.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="audit-actor">Admin</Label>
            <Select items={actorItems} value={actor} onValueChange={(value) => setActor(value ?? ALL)}>
              <SelectTrigger id="audit-actor" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {actorItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="audit-action">Aktion</Label>
            <Select items={actionItems} value={action} onValueChange={(value) => setAction(value ?? ALL)}>
              <SelectTrigger id="audit-action" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {actionItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        {log.isPending ? (
          <LoadingState label="Audit-Log wird geladen …" />
        ) : log.isError && entries.length === 0 ? (
          <ErrorState message={ERROR_MESSAGE} onRetry={() => log.refetch()} />
        ) : entries.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {filtered ? 'Keine Einträge für diesen Filter.' : 'Noch keine Einträge.'}
          </p>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Zeit</TableHead>
                  <TableHead>Admin</TableHead>
                  <TableHead>Aktion</TableHead>
                  <TableHead>Beschreibung</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="text-muted-foreground">{formatTime(entry.occurredAt)}</TableCell>
                    <TableCell>
                      <ActorName actor={entry.actor} />
                    </TableCell>
                    <TableCell>{ACTIONS[entry.action]}</TableCell>
                    <TableCell className="whitespace-normal">{entry.description}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {log.isFetchNextPageError && <ErrorState message={ERROR_MESSAGE} onRetry={() => log.fetchNextPage()} />}
            {log.hasNextPage && !log.isFetchNextPageError && (
              <Button
                variant="outline"
                className="self-center"
                disabled={log.isFetchingNextPage}
                onClick={() => log.fetchNextPage()}
              >
                {log.isFetchingNextPage ? 'Wird geladen …' : 'Mehr laden'}
              </Button>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function ActorName({ actor }: { actor: AuditActor }) {
  return (
    <span className="flex items-center gap-2">
      <Avatar size="sm">
        {actor.avatarUrl && <AvatarImage src={actor.avatarUrl} alt="" />}
        <AvatarFallback>{actor.name.slice(0, 1).toUpperCase()}</AvatarFallback>
      </Avatar>
      {actor.name}
      {actor.kind === 'ADMIN_ACCOUNT' && <Badge variant="outline">Admin-Konto</Badge>}
    </span>
  )
}

function actorLabel(actor: AuditActor): string {
  return actor.kind === 'ADMIN_ACCOUNT' ? `${actor.name} (Admin-Konto)` : actor.name
}
