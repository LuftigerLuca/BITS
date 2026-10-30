import { Fragment, useState } from 'react'
import { IconAction } from '@/components/IconAction'
import { PencilIcon, Trash2Icon } from 'lucide-react'
import { EmptyState, ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { RoleRule } from '@/lib/api'
import { STATUS, errorMessage } from './labels'
import { RulePreview } from './RulePreview'
import { useDeleteRoleRule, useDeletionPreview, useRoleRules } from './queries'

/** Rules that tie Discord roles to WarEra data; BITS gives and takes these roles on its own. */
export function RoleRulesCard({ onEdit = () => {} }: { onEdit?: (rule: RoleRule) => void }) {
  const rules = useRoleRules()
  const deleteRule = useDeleteRoleRule()
  const [ruleToDelete, setRuleToDelete] = useState<RoleRule | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Automatische Rollen</CardTitle>
        <CardDescription>
          BITS vergibt diese Rollen anhand der WarEra-Daten verknüpfter Accounts und entzieht sie allen, die keine ihrer
          Regel erfüllen. Von den Blöcken einer Regel genügt einer, die Bedingungen eines Blocks müssen alle erfüllt
          sein.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {rules.isPending ? (
          <LoadingState label="Regeln werden geladen …" />
        ) : rules.isError ? (
          <ErrorState message={errorMessage(rules.error)} onRetry={() => rules.refetch()} />
        ) : rules.data.length === 0 ? (
          <EmptyState title="Noch keine Regeln angelegt" description="Lege unter „Neue Regel“ die erste an." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Rolle</TableHead>
                <TableHead>Bedingungen</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-0" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.data.map((rule) => (
                <TableRow key={rule.roleId}>
                  <TableCell>
                    {rule.roleName !== null ? (
                      <RoleName name={rule.roleName} color={rule.roleColor} />
                    ) : (
                      <span className="text-muted-foreground">Gelöschte Rolle</span>
                    )}
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    <Blocks rule={rule} />
                  </TableCell>
                  <TableCell>
                    <Badge variant={STATUS[rule.status].variant}>{STATUS[rule.status].label}</Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex">
                      {/* Orphaned and unassignable roles cannot get a new rule, only lose theirs. */}
                      {rule.status === 'ACTIVE' && (
                        <IconAction label={`Regel „${rule.label}“ bearbeiten`} onClick={() => onEdit(rule)}>
                          <PencilIcon />
                        </IconAction>
                      )}
                      <IconAction
                        label={`Regel „${rule.label}“ löschen`}
                        disabled={deleteRule.isPending && deleteRule.variables === rule.roleId}
                        onClick={() => setRuleToDelete(rule)}
                      >
                        <Trash2Icon />
                      </IconAction>
                    </div>
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
            deleteRule.mutate(rule.roleId)
          }}
          onCancel={() => setRuleToDelete(null)}
        />
      </CardContent>
    </Card>
  )
}

/** The blocks of a rule, with „oder“ between them, so the grouping of the conditions shows. */
function Blocks({ rule }: { rule: RoleRule }) {
  return (
    <div className="flex flex-col gap-1">
      {rule.blocks.map((block, index) => (
        <Fragment key={index}>
          {index > 0 && <span className="text-xs font-medium text-muted-foreground">oder</span>}
          <span>{block.label}</span>
        </Fragment>
      ))}
    </div>
  )
}

/**
 * Deleting a rule ends the management of its role, which takes the role from nobody; the dialog says so, and the sync
 * that follows runs right away.
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
  const preview = useDeletionPreview(rule?.roleId ?? null)
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
          <AlertDialogAction variant="destructive" disabled={preview.isPending} onClick={() => rule && onConfirm(rule)}>
            Regel löschen
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
