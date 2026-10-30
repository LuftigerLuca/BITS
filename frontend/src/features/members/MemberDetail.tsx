import { useState } from 'react'
import { Link } from 'react-router'
import { EmptyState, ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Rules } from '@/features/account/RolesCard'
import { OUTCOMES } from '@/features/account/labels'
import { formatTime } from '@/features/system-log/labels'
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
import type { MemberDetail as Member } from '@/lib/api'
import { ON_SERVER, describeEvent, memberErrorMessage, rolesErrorMessage } from './labels'
import { useMember, useMemberRoles, useSyncMember, useUnlinkMember } from './queries'

/** Link, managed roles and recent system log of one member, with a sync and unlinking. */
export function MemberDetail({ discordUserId }: { discordUserId: string }) {
  const member = useMember(discordUserId)

  if (member.isPending) return <LoadingState label="Mitglied wird geladen …" />
  if (member.isError) return <ErrorState message={memberErrorMessage(member.error)} onRetry={() => member.refetch()} />
  return (
    <>
      <MemberCard member={member.data} />
      <RolesCard discordUserId={discordUserId} />
      <LogCard member={member.data} />
    </>
  )
}

function MemberCard({ member }: { member: Member }) {
  const sync = useSyncMember()
  const unlink = useUnlinkMember()
  const [confirming, setConfirming] = useState(false)
  const name = member.discordName ?? member.discordUserId

  return (
    <Card>
      <CardHeader>
        <CardTitle>{name}</CardTitle>
        <CardDescription>
          „Jetzt abgleichen“ prüft das Mitglied mit frischen WarEra-Daten und gleicht seine Rollen sofort ab.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 text-sm">
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
          <dt className="text-muted-foreground">Discord-ID</dt>
          <dd>{member.discordUserId}</dd>
          <dt className="text-muted-foreground">Auf dem Server</dt>
          <dd>{ON_SERVER(member.onServer)}</dd>
          <dt className="text-muted-foreground">WarEra-Account</dt>
          <dd className="font-medium">{member.link?.wareraUsername ?? 'nicht verknüpft'}</dd>
          {member.link && (
            <>
              <dt className="text-muted-foreground">Verknüpft am</dt>
              <dd>{formatTime(member.link.verifiedAt)}</dd>
            </>
          )}
        </dl>
        <div className="flex flex-wrap gap-2">
          <Button disabled={sync.isPending} onClick={() => sync.mutate(member.discordUserId)}>
            Jetzt abgleichen
          </Button>
          {member.link && (
            <Button variant="destructive" disabled={unlink.isPending} onClick={() => setConfirming(true)}>
              Verknüpfung lösen
            </Button>
          )}
        </div>
        {member.link && (
          <AlertDialog open={confirming} onOpenChange={setConfirming}>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Verknüpfung lösen?</AlertDialogTitle>
                <AlertDialogDescription>
                  {name} ist dann nicht mehr mit <strong>{member.link.wareraUsername}</strong> verknüpft und verliert
                  die Rollen, die BITS verwaltet, beim folgenden Abgleich, wie bei <code>/unverify</code>. Mit{' '}
                  <code>/verify</code> kann sich das Mitglied neu verknüpfen. Das Lösen steht im Audit-Log.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Abbrechen</AlertDialogCancel>
                <AlertDialogAction
                  variant="destructive"
                  onClick={() => {
                    setConfirming(false)
                    unlink.mutate(member.discordUserId)
                  }}
                >
                  Verknüpfung lösen
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        )}
      </CardContent>
    </Card>
  )
}

function RolesCard({ discordUserId }: { discordUserId: string }) {
  const roles = useMemberRoles(discordUserId)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Rollen</CardTitle>
        <CardDescription>
          Die Rollen, die BITS nach den aktuellen WarEra-Daten des Mitglieds vergibt, und ob es sie auf dem Server
          gerade hat. Weicht beides ab, bringt der nächste Abgleich es in Ordnung.
        </CardDescription>
      </CardHeader>
      <CardContent className="text-sm">
        {roles.isPending ? (
          <LoadingState label="Rollen werden geprüft …" />
        ) : roles.isError ? (
          <ErrorState message={rolesErrorMessage(roles.error)} onRetry={() => roles.refetch()} />
        ) : !roles.data.linked ? (
          <p className="text-muted-foreground">Ohne verknüpften WarEra-Account vergibt BITS keine Rollen.</p>
        ) : roles.data.roles.length === 0 ? (
          <EmptyState title="BITS verwaltet noch keine Rollen" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rolle</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Auf dem Server</TableHead>
                <TableHead>Regeln</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {roles.data.roles.map((role) => (
                <TableRow key={role.id}>
                  <TableCell>
                    <RoleName name={role.name} color={role.color} />
                  </TableCell>
                  <TableCell>
                    <Badge variant={OUTCOMES[role.outcome].variant}>{OUTCOMES[role.outcome].label}</Badge>
                  </TableCell>
                  <TableCell>{role.held === null ? 'unbekannt' : role.held ? 'hat sie' : 'hat sie nicht'}</TableCell>
                  <TableCell className="whitespace-normal">
                    <Rules rules={role.rules} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function LogCard({ member }: { member: Member }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Letzte Einträge im System-Log</CardTitle>
        <CardDescription>
          Rollenänderungen und Account-Ereignisse des Mitglieds, neueste zuerst.{' '}
          <Link
            to={`/admin/system-log?${new URLSearchParams({ member: member.discordUserId })}`}
            className="underline underline-offset-4"
          >
            Alle Einträge im System-Log
          </Link>
        </CardDescription>
      </CardHeader>
      <CardContent className="text-sm">
        {member.recentLog.length === 0 ? (
          <EmptyState title="Das System-Log enthält nichts zu diesem Mitglied" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Zeit</TableHead>
                <TableHead>Ereignis</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {member.recentLog.map((event, index) => (
                <TableRow key={index}>
                  <TableCell className="text-muted-foreground">{formatTime(event.occurredAt)}</TableCell>
                  <TableCell className="whitespace-normal">{describeEvent(event)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
