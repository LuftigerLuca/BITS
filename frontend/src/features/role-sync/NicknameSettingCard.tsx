import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { NICKNAME_ERROR_MESSAGE, NICKNAME_SAVE_ERROR_MESSAGE } from './labels'
import { useNicknameSetting, useSetNicknameSetting } from './queries'

/** Whether the role sync sets the nicknames of linked members to their WarEra names. */
export function NicknameSettingCard() {
  const setting = useNicknameSetting()
  const setEnabled = useSetNicknameSetting()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Nicknames</CardTitle>
        <CardDescription>
          Verknüpfte Mitglieder tragen ihren WarEra-Namen als Nickname. BITS gleicht ihn beim Verknüpfen und stündlich an
          und entfernt ihn beim Lösen der Verknüpfung, sofern er noch dem WarEra-Namen entspricht. Der Bot braucht dafür
          die Berechtigung „Nicknames verwalten“ und kann den Server-Besitzer sowie Mitglieder über seiner Rolle nicht
          umbenennen.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {setting.isPending ? (
          <LoadingState label="Einstellung wird geladen …" />
        ) : setting.isError ? (
          <ErrorState message={NICKNAME_ERROR_MESSAGE} onRetry={() => setting.refetch()} />
        ) : (
          <>
            {setEnabled.isError && (
              <Alert variant="destructive">
                <AlertDescription>{NICKNAME_SAVE_ERROR_MESSAGE}</AlertDescription>
              </Alert>
            )}
            <div className="flex items-center gap-3">
              <Switch
                id="nickname-setting"
                checked={setting.data.enabled}
                disabled={setEnabled.isPending}
                onCheckedChange={(enabled) => setEnabled.mutate(enabled)}
              />
              <Label htmlFor="nickname-setting">Nickname auf den WarEra-Namen setzen</Label>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
