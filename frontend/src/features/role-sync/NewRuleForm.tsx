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
import { Separator } from '@/components/ui/separator'
import { useCountries } from '@/lib/countries'
import {
  MAX_BLOCKS,
  MAX_CONDITIONS,
  type CreateCondition,
  type CreateRoleRule,
  type DiscordRole,
  type RoleRule,
} from '@/lib/api'
import { ConditionFields } from './ConditionFields'
import { blocksOf, draftsOf, needsCountry, newDraft, toCondition, type ConditionDraft } from './conditionDraft'
import { errorMessage } from './labels'
import { RulePreview } from './RulePreview'
import { useCreateRoleRule, useRulePreview, useUpdateRoleRule } from './queries'

/**
 * Form for the rule of a role: conditions combined with AND or OR, where AND binds tighter, so the ORs split them into
 * blocks of which one is enough. It creates a rule, or with [editing] replaces that rule as a whole; it shows whom the
 * change gives and takes the role and saves it only once confirmed.
 */
export function NewRuleForm({
  roles,
  rules,
  editing,
  onEdit,
  onDone,
}: {
  roles: DiscordRole[]
  rules: RoleRule[]
  /** The rule being edited; its role cannot be changed. */
  editing: RoleRule | null
  /** Switches to editing the rule a role has already. */
  onEdit: (rule: RoleRule) => void
  /** Ends editing, after saving or cancelling. */
  onDone: () => void
}) {
  const [roleId, setRoleId] = useState<string | null>(editing?.roleId ?? null)
  const [drafts, setDrafts] = useState<ConditionDraft[]>(() => (editing ? draftsOf(editing.blocks) : [newDraft(0)]))
  const nextKey = useRef(drafts.length)
  /** The rule waiting for confirmation. */
  const [pending, setPending] = useState<CreateRoleRule | null>(null)
  /** Only the MU search reports here; the result of saving is a toast. */
  const [searchError, setSearchError] = useState<string | null>(null)
  const createRule = useCreateRoleRule()
  const updateRule = useUpdateRoleRule()
  const saving = editing ? updateRule : createRule
  const preview = useRulePreview(pending)

  const countries = useCountries(drafts.some((draft) => needsCountry(draft.type)))

  const role = roles.find((r) => r.id === roleId)
  /** The rule the picked role has already, which is to be edited instead of creating another. */
  const existing = editing ? undefined : rules.find((rule) => rule.roleId === roleId)
  const blocks = blocksOf(drafts, toCondition)
  const complete = blocks.every((block) => block.every((condition) => condition !== null))
  // The backend rejects the same condition twice in a block and the same block twice; each type builds its fields in
  // the same order, and the conditions of a block may come in any order.
  const blockKeys = blocks.map((block) => block.map((condition) => JSON.stringify(condition)))
  const duplicateCondition = blockKeys.some((keys) => new Set(keys).size < keys.length)
  const duplicateBlock = new Set(blockKeys.map((keys) => [...keys].sort().join(';'))).size < blockKeys.length
  const tooMany = blocks.length > MAX_BLOCKS || blocks.some((block) => block.length > MAX_CONDITIONS)

  function rule(): CreateRoleRule | null {
    if (!roleId || existing || !complete || duplicateCondition || duplicateBlock || tooMany) return null
    return { roleId, blocks: blocks as CreateCondition[][] }
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

  function save() {
    if (!pending) return
    setPending(null)
    saving.mutate(pending, {
      onSuccess: () => {
        // Ready for the next rule; an edited one is shown in the list.
        setRoleId(null)
        setDrafts([newDraft(nextKey.current++)])
        onDone()
      },
    })
  }

  // The roles of DSKs are given by their DSK, see the page "Militäreinheiten".
  const roleItems = roles.filter((r) => r.dskName === null).map((r) => ({ value: r.id, label: r.name }))
  const colors = new Map(roles.map((r) => [r.id, r.color]))
  const action = editing ? 'Regel speichern' : 'Regel anlegen'

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      {searchError && (
        <Alert variant="destructive">
          <AlertDescription>{searchError}</AlertDescription>
        </Alert>
      )}
      {countries.isError && <ErrorState message={errorMessage(countries.error)} onRetry={() => countries.refetch()} />}
      <div className="flex flex-col gap-1.5 sm:max-w-[calc(50%-0.5rem)]">
        <Label htmlFor="rule-role">Rolle</Label>
        <SearchableSelect
          id="rule-role"
          className="w-full"
          items={roleItems}
          value={roleId}
          onValueChange={setRoleId}
          disabled={editing !== null}
          placeholder="Rolle wählen"
          renderItem={(item) => <RoleName name={item.label} color={colors.get(item.value) ?? null} />}
        />
        <p className="text-xs text-muted-foreground">
          Nur Rollen unter der höchsten Rolle des Bots, ohne @everyone, Integrationsrollen und die Rollen der DSKs.
        </p>
      </div>
      {existing && (
        <Alert>
          <AlertDescription className="flex flex-wrap items-center gap-x-3 gap-y-2">
            Für diese Rolle gibt es schon eine Regel. Ergänze sie, statt eine zweite anzulegen.
            <Button type="button" variant="outline" size="sm" onClick={() => onEdit(existing)}>
              Regel bearbeiten
            </Button>
          </AlertDescription>
        </Alert>
      )}
      <div className="flex flex-col gap-2">
        {drafts.map((draft, index) => (
          <Fragment key={draft.key}>
            {index > 0 && (
              <Operator position={index + 1} or={draft.or} onChange={(or) => changeCondition({ ...draft, or })} />
            )}
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
      {duplicateCondition && <p className="text-sm text-destructive">Eine Bedingung steht doppelt in einem Block.</p>}
      {duplicateBlock && <p className="text-sm text-destructive">Ein Block steht doppelt in der Regel.</p>}
      {tooMany && (
        <p className="text-sm text-destructive">
          Höchstens {MAX_CONDITIONS} Bedingungen je Block und {MAX_BLOCKS} Blöcke je Regel.
        </p>
      )}
      <Button
        type="button"
        variant="outline"
        className="self-start"
        onClick={addCondition}
        disabled={drafts.length >= MAX_BLOCKS * MAX_CONDITIONS}
      >
        <PlusIcon />
        Bedingung hinzufügen
      </Button>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={saving.isPending || rule() === null}>
          {action}
        </Button>
        {editing && (
          <Button type="button" variant="outline" onClick={onDone}>
            Abbrechen
          </Button>
        )}
      </div>
      <AlertDialog open={pending !== null} onOpenChange={(open) => !open && setPending(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            {/* The first rule of a role hands the role over to BITS, which takes it from everybody without a match. */}
            <AlertDialogTitle>
              {editing ? 'Regel speichern?' : role?.managed ? 'Regel anlegen?' : 'Rolle an BITS übergeben?'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {editing || role?.managed
                ? `BITS gleicht die Rolle ${role?.name} danach sofort ab.`
                : `Die Rolle ${role?.name} wird ab jetzt von BITS verwaltet; Mitglieder ohne passende Regel verlieren sie, auch wenn sie sie von Hand bekommen haben.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <RulePreview preview={preview} />
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction onClick={save} disabled={preview.isPending}>
              {action}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </form>
  )
}

/**
 * Whether the condition at [position] is combined with the one before by AND or by OR. An OR starts a new block, so it
 * is drawn as a line across the form.
 */
function Operator({ position, or, onChange }: { position: number; or: boolean; onChange: (or: boolean) => void }) {
  return (
    <div className="flex items-center gap-3">
      {or ? <Separator className="flex-1" /> : <div className="flex-1" />}
      <div role="group" aria-label={`Verknüpfung vor Bedingung ${position}`} className="flex rounded-md border p-0.5">
        {[false, true].map((value) => (
          <Button
            key={String(value)}
            type="button"
            size="xs"
            variant={or === value ? 'secondary' : 'ghost'}
            aria-pressed={or === value}
            onClick={() => onChange(value)}
          >
            {value ? 'oder' : 'und'}
          </Button>
        ))}
      </div>
      {or ? <Separator className="flex-1" /> : <div className="flex-1" />}
    </div>
  )
}
