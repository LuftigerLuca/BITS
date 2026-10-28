import { useEffect, useRef } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { RoleRule } from '@/lib/api'
import { NewRuleForm } from './NewRuleForm'
import { errorMessage } from './labels'
import { useRoleRules, useRoles } from './queries'

/** Creates the rule of one of the roles the bot can give, or with [editing] edits that rule. */
export function NewRuleCard({
  editing = null,
  onEdit = () => {},
  onDone = () => {},
}: {
  editing?: RoleRule | null
  onEdit?: (rule: RoleRule) => void
  onDone?: () => void
}) {
  const roles = useRoles()
  const rules = useRoleRules()
  const card = useRef<HTMLDivElement>(null)

  // The card is below the list of rules, where editing starts.
  useEffect(() => {
    if (editing) card.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  }, [editing])

  return (
    <Card ref={card}>
      <CardHeader>
        <CardTitle>{editing ? `Regel für ${editing.roleName} bearbeiten` : 'Neue Regel'}</CardTitle>
        <CardDescription>
          Jede Rolle hat eine Regel. Mit „und“ verknüpfte Bedingungen müssen alle erfüllt sein, von den mit „oder“
          getrennten Blöcken genügt einer; „und“ bindet stärker als „oder“.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {roles.isPending || rules.isPending ? (
          <LoadingState label="Rollen werden geladen …" />
        ) : roles.isError ? (
          <ErrorState message={errorMessage(roles.error)} onRetry={() => roles.refetch()} />
        ) : rules.isError ? (
          <ErrorState message={errorMessage(rules.error)} onRetry={() => rules.refetch()} />
        ) : (
          // A new key starts the form afresh for each rule edited.
          <NewRuleForm
            key={editing?.roleId ?? 'new'}
            roles={roles.data}
            rules={rules.data}
            editing={editing}
            onEdit={onEdit}
            onDone={onDone}
          />
        )}
      </CardContent>
    </Card>
  )
}
