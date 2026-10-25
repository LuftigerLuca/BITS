import { useState } from 'react'
import { Trash2Icon } from 'lucide-react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { RoleRule } from '@/lib/api'
import { NewRuleForm } from './NewRuleForm'
import { STATUS, errorMessage } from './labels'
import { RulePreview } from './RulePreview'
import { useDeleteRoleRule, useDeletionPreview, useRoleRules, useRoles } from './queries'

/** Rules that tie Discord roles to WarEra data; BITS gives and takes these roles on its own. */
export function RoleRulesCard() {
  const roles = useRoles()
  const rules = useRoleRules()
  const deleteRule = useDeleteRoleRule()
  const [ruleToDelete, setRuleToDelete] = useState<RoleRule | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Automatische Rollen</CardTitle>
        <CardDescription>
          BITS vergibt diese Rollen anhand der WarEra-Daten verknüpfter Accounts und entzieht sie allen, die keine
          ihrer Regeln erfüllen. Mehrere Regeln für dieselbe Rolle reichen einzeln aus.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {deleteRule.isError && (
          <Alert variant="destructive">
            <AlertDescription>{errorMessage(deleteRule.error)}</AlertDescription>
          </Alert>
        )}
        {rules.isPending ? (
          <LoadingState label="Regeln werden geladen …" />
        ) : rules.isError ? (
          <ErrorState message={errorMessage(rules.error)} onRetry={() => rules.refetch()} />
        ) : rules.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine Regeln angelegt.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rolle</TableHead>
                <TableHead>Bedingung</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-0" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.data.map((rule) => (
                <TableRow key={rule.id}>
                  <TableCell>
                    {rule.roleName !== null ? (
                      <RoleName name={rule.roleName} color={rule.roleColor} />
                    ) : (
                      <span className="text-muted-foreground">Gelöschte Rolle</span>
                    )}
                  </TableCell>
                  <TableCell>{rule.label}</TableCell>
                  <TableCell>
                    <Badge variant={STATUS[rule.status].variant}>{STATUS[rule.status].label}</Badge>
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`Regel „${rule.label}“ löschen`}
                      disabled={deleteRule.isPending && deleteRule.variables === rule.id}
                      onClick={() => setRuleToDelete(rule)}
                    >
                      <Trash2Icon />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <DeleteRuleDialog
          rule={ruleToDelete}
          onConfirm={(rule) => {
            setRuleToDelete(null)
            deleteRule.mutate(rule.id)
          }}
          onCancel={() => setRuleToDelete(null)}
        />
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

/**
 * Deleting a rule syncs everybody right away, so a slip in the table would take the role from members at once; the
 * dialog shows from whom. The last rule of a role ends its management instead, which takes the role from nobody.
 */
function DeleteRuleDialog({
  rule,
  onConfirm,
  onCancel,
}: {
  rule: RoleRule | null
  onConfirm: (rule: RoleRule) => void
  onCancel: () => void
}) {
  const preview = useDeletionPreview(rule?.id ?? null)
  const role = rule?.roleName ?? 'gelöschte Rolle'
  return (
    <AlertDialog open={rule !== null} onOpenChange={(open) => !open && onCancel()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Regel löschen?</AlertDialogTitle>
          <AlertDialogDescription>
            {rule && (
              <>
                <strong>{role}</strong>: {rule.label}.
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {rule && <RulePreview preview={preview} />}
        <AlertDialogFooter>
          <AlertDialogCancel>Abbrechen</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={preview.isPending}
            onClick={() => rule && onConfirm(rule)}
          >
            Regel löschen
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
