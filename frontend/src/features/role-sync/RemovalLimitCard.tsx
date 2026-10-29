import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Item, ItemActions, ItemContent, ItemDescription, ItemTitle } from '@/components/ui/item'
import { Switch } from '@/components/ui/switch'
import { errorMessage } from './labels'
import { useRemovalLimit, useSetRemovalLimit } from './queries'

/** Switches off the limit that holds back a role a sync of all members would take from many of its holders. */
export function RemovalLimitCard() {
  const removalLimit = useRemovalLimit()
  const setRemovalLimit = useSetRemovalLimit()
  return (
    <Card>
      <CardHeader>
        <CardTitle>Schutz vor Massenentzug</CardTitle>
        <CardDescription>
          Würde der stündliche Abgleich einer Rolle mindestens 10 Mitgliedern und mehr als einem Viertel ihrer Inhaber
          auf einmal entziehen, hält BITS das zurück. Meist fehlen dann WarEra-Daten. Ist die Änderung echt, schalte den
          Schutz aus; BITS gleicht dann sofort alle Mitglieder ab.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {removalLimit.isPending ? (
          <LoadingState label="Einstellung wird geladen …" />
        ) : removalLimit.isError ? (
          <ErrorState message={errorMessage(removalLimit.error)} onRetry={() => removalLimit.refetch()} />
        ) : (
          <>
            <Item variant="outline">
              <ItemContent>
                <ItemTitle id="removal-limit-label">Schutz aktiv</ItemTitle>
                <ItemDescription id="removal-limit-description" className="line-clamp-none">
                  Gilt nicht für Rollen, deren Regel du gerade geändert hast, und nicht beim Abgleich einzelner
                  Mitglieder.
                </ItemDescription>
              </ItemContent>
              <ItemActions>
                <Switch
                  aria-labelledby="removal-limit-label"
                  aria-describedby="removal-limit-description"
                  checked={removalLimit.data.enabled}
                  disabled={setRemovalLimit.isPending}
                  onCheckedChange={(checked) => setRemovalLimit.mutate(checked)}
                />
              </ItemActions>
            </Item>
            {!removalLimit.data.enabled && (
              <Alert>
                <AlertDescription>
                  Der Schutz ist aus: Fehlen WarEra-Daten, kann BITS vielen Mitgliedern auf einmal Rollen entziehen.
                  Schalte ihn wieder ein, sobald die Änderung durch ist.
                </AlertDescription>
              </Alert>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
