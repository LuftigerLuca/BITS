import { Fragment, useRef, useState, type FormEvent } from 'react'
import { PlusIcon } from 'lucide-react'
import { ErrorState } from '@/components/QueryState'
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
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { SearchableSelect } from '@/components/ui/searchable-select'
import { useCountries } from '@/lib/countries'
import { MAX_CONDITIONS, type CreateCondition, type CreateRoleRule, type DiscordRole } from '@/lib/api'
import { ConditionFields } from './ConditionFields'
import { needsCountry, newDraft, toCondition, type ConditionDraft } from './conditionDraft'
import { errorMessage } from './labels'
import { RulePreview } from './RulePreview'
import { useCreatePreview, useCreateRoleRule } from './queries'

/**
 * Form for a new rule with one or more conditions, all of which must be met; it shows whom the rule gives and takes the
 * role and saves it only once confirmed.
 */
export function NewRuleForm({ roles }: { roles: DiscordRole[] }) {
  const [roleId, setRoleId] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<ConditionDraft[]>(() => [newDraft(0)])
  const nextKey = useRef(1)
  /** The rule waiting for confirmation. */
  const [pending, setPending] = useState<CreateRoleRule | null>(null)
  /** Only the MU search reports here; the result of saving is a toast. */
  const [searchError, setSearchError] = useState<string | null>(null)
  const createRule = useCreateRoleRule()
  const preview = useCreatePreview(pending)

  const countries = useCountries(drafts.some((draft) => needsCountry(draft.type)))

  const role = roles.find((r) => r.id === roleId)
  const conditions = drafts.map(toCondition)
  const complete = conditions.filter((condition): condition is CreateCondition => condition !== null)
  // The backend rejects the same condition twice; each type builds its fields in the same order.
  const duplicate = new Set(complete.map((condition) => JSON.stringify(condition))).size < complete.length

  function rule(): CreateRoleRule | null {
    if (!roleId || complete.length < conditions.length || duplicate) return null
    return { roleId, conditions: complete }
  }

  function addCondition() {
    setDrafts((current) => [...current, newDraft(nextKey.current++)])
  }

  function changeCondition(changed: ConditionDraft) {
    setDrafts((current) => current.map((draft) => (draft.key === changed.key ? changed : draft)))
  }

  function removeCondition(key: number) {
    setDrafts((current) => current.filter((draft) => draft.key !== key))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    setPending(rule())
  }

  function create() {
    if (!pending) return
    setPending(null)
    createRule.mutate(pending)
  }

  const roleItems = roles.map((r) => ({ value: r.id, label: r.name }))
  const colors = new Map(roles.map((r) => [r.id, r.color]))

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      {searchError && (
        <Alert variant="destructive">
          <AlertDescription>{searchError}</AlertDescription>
        </Alert>
      )}
      {countries.isError && (
        <ErrorState message={errorMessage(countries.error)} onRetry={() => countries.refetch()} />
      )}
      <div className="flex flex-col gap-1.5 sm:max-w-[calc(50%-0.5rem)]">
        <Label htmlFor="rule-role">Rolle</Label>
        <SearchableSelect
          id="rule-role"
          className="w-full"
          items={roleItems}
          value={roleId}
          onValueChange={setRoleId}
          placeholder="Rolle wählen"
          renderItem={(item) => <RoleName name={item.label} color={colors.get(item.value) ?? null} />}
        />
        <p className="text-xs text-muted-foreground">
          Nur Rollen unter der höchsten Rolle des Bots, ohne @everyone und Integrationsrollen.
        </p>
      </div>
      <div className="flex flex-col gap-2">
        {drafts.map((draft, index) => (
          <Fragment key={draft.key}>
            {index > 0 && <p className="text-center text-xs font-medium text-muted-foreground">und</p>}
            <ConditionFields
              name={`Bedingung ${index + 1}`}
              draft={draft}
              onChange={changeCondition}
              onRemove={drafts.length > 1 ? () => removeCondition(draft.key) : undefined}
              countries={countries.data ?? []}
              countriesPending={countries.isPending}
              onSearchError={setSearchError}
            />
          </Fragment>
        ))}
      </div>
      {duplicate && <p className="text-sm text-destructive">Eine Bedingung steht doppelt in der Regel.</p>}
      <Button
        type="button"
        variant="outline"
        className="self-start"
        onClick={addCondition}
        disabled={drafts.length >= MAX_CONDITIONS}
      >
        <PlusIcon />
        Bedingung hinzufügen
      </Button>
      <Button type="submit" className="self-start" disabled={createRule.isPending || rule() === null}>
        Regel anlegen
      </Button>
      <AlertDialog open={pending !== null} onOpenChange={(open) => !open && setPending(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            {/* The first rule of a role hands the role over to BITS, which takes it from everybody without a match. */}
            <AlertDialogTitle>{role?.managed ? 'Regel anlegen?' : 'Rolle an BITS übergeben?'}</AlertDialogTitle>
            <AlertDialogDescription>
              {role?.managed
                ? `BITS gleicht die Rolle ${role.name} danach sofort ab.`
                : `Die Rolle ${role?.name} wird ab jetzt von BITS verwaltet; Mitglieder ohne passende Regel verlieren sie, auch wenn sie sie von Hand bekommen haben.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <RulePreview preview={preview} />
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction onClick={create} disabled={preview.isPending}>
              Regel anlegen
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </form>
  )
}
