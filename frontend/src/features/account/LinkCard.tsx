import { useState } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
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
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ACCOUNT_ERROR, UNLINK_ERROR, formatTime } from './labels'
import { useAccount, useUnlinkAccount } from './queries'

/** The WarEra account the member linked with `/verify`, and the way to undo it like `/unverify`. */
export function LinkCard() {
  const account = useAccount()
  const unlink = useUnlinkAccount()
  const [confirming, setConfirming] = useState(false)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Verknüpfung</CardTitle>
        <CardDescription>Der WarEra-Account, nach dem BITS deine Rollen vergibt.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 text-sm">
        {unlink.isError && (
          <Alert variant="destructive">
            <AlertDescription>{UNLINK_ERROR}</AlertDescription>
          </Alert>
        )}
        {unlink.isSuccess && (
          <Alert>
            <AlertDescription>
              Verknüpfung gelöst. Die Rollen, die BITS verwaltet, verlierst du beim folgenden Abgleich.
            </AlertDescription>
          </Alert>
        )}
        {account.isPending ? (
          <LoadingState />
        ) : account.isError ? (
          <ErrorState message={ACCOUNT_ERROR} onRetry={() => account.refetch()} />
        ) : account.data.link === null ? (
          <p className="text-muted-foreground">
            Dein Discord-Account ist mit keinem WarEra-Account verknüpft. Mit <code>/verify</code> im Discord kannst du
            ihn verknüpfen.
          </p>
        ) : (
          <>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
              <dt className="text-muted-foreground">WarEra-Account</dt>
              <dd className="font-medium">{account.data.link.wareraUsername}</dd>
              <dt className="text-muted-foreground">Verknüpft am</dt>
              <dd>{formatTime(account.data.link.verifiedAt)}</dd>
              <dt className="text-muted-foreground">Letzter Abgleich</dt>
              <dd>{account.data.lastSyncAt !== null ? formatTime(account.data.lastSyncAt) : 'noch keiner bekannt'}</dd>
            </dl>
            <div>
              <Button variant="destructive" disabled={unlink.isPending} onClick={() => setConfirming(true)}>
                Verknüpfung lösen
              </Button>
            </div>
            <AlertDialog open={confirming} onOpenChange={setConfirming}>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Verknüpfung lösen?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Dein Discord-Account ist dann nicht mehr mit <strong>{account.data.link.wareraUsername}</strong>{' '}
                    verknüpft. Die Rollen, die BITS verwaltet, verlierst du beim folgenden Abgleich. Mit{' '}
                    <code>/verify</code> kannst du dich jederzeit neu verknüpfen.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Abbrechen</AlertDialogCancel>
                  <AlertDialogAction
                    variant="destructive"
                    onClick={() => {
                      setConfirming(false)
                      unlink.mutate()
                    }}
                  >
                    Verknüpfung lösen
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </>
        )}
      </CardContent>
    </Card>
  )
}
