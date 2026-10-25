import { ErrorState, LoadingState } from '@/components/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { NewRuleForm } from './NewRuleForm'
import { errorMessage } from './labels'
import { useRoles } from './queries'

/** Creates a rule for one of the roles the bot can give. */
export function NewRuleCard() {
  const roles = useRoles()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Neue Regel</CardTitle>
        <CardDescription>
          Eine Regel greift, wenn alle ihre Bedingungen erfüllt sind. Von mehreren Regeln einer Rolle genügt eine.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {roles.isPending ? (
          <LoadingState label="Rollen werden geladen …" />
        ) : roles.isError ? (
          <ErrorState message={errorMessage(roles.error)} onRetry={() => roles.refetch()} />
        ) : (
          <NewRuleForm roles={roles.data} />
        )}
      </CardContent>
    </Card>
  )
}
