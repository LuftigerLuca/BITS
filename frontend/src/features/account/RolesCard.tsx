import { CheckIcon, CircleHelpIcon, XIcon } from 'lucide-react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AccountRole } from '@/lib/api'
import { OUTCOMES, rolesErrorMessage } from './labels'
import { useAccountRoles } from './queries'

/** Which managed roles the member meets and why, as `/rollen` tells them in Discord. */
export function RolesCard() {
  const roles = useAccountRoles()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Rollen</CardTitle>
        <CardDescription>
          Die Rollen, die BITS nach deinen aktuellen WarEra-Daten vergibt. Eine erfüllte Regel reicht für die Rolle;
          auf dem Server ändert sich deine Rolle beim nächsten Abgleich.
        </CardDescription>
      </CardHeader>
      <CardContent className="text-sm">
        {roles.isPending ? (
          <LoadingState label="Rollen werden geprüft …" />
        ) : roles.isError ? (
          <ErrorState message={rolesErrorMessage(roles.error)} onRetry={() => roles.refetch()} />
        ) : !roles.data.linked ? (
          <p className="text-muted-foreground">
            Ohne verknüpften WarEra-Account vergibt BITS dir keine Rollen. Mit <code>/verify</code> im Discord kannst
            du ihn verknüpfen.
          </p>
        ) : roles.data.roles.length === 0 ? (
          <p className="text-muted-foreground">BITS verwaltet noch keine Rollen.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rolle</TableHead>
                <TableHead>Status</TableHead>
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

/** The blocks of a role with whether each is met, as `/rollen` lists them. */
export function Rules({ rules }: { rules: AccountRole['rules'] }) {
  return (
    <ul className="flex flex-col gap-1">
      {rules.map((rule) => (
        <li key={rule.label} className="flex items-start gap-2">
          {rule.met === true ? (
            <CheckIcon className="mt-0.5 size-4 shrink-0 text-primary" aria-label="erfüllt" />
          ) : rule.met === false ? (
            <XIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-label="nicht erfüllt" />
          ) : (
            <CircleHelpIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-label="offen" />
          )}
          <span>
            {rule.label}
            {rule.detail && <span className="text-muted-foreground"> ({rule.detail})</span>}
          </span>
        </li>
      ))}
    </ul>
  )
}
