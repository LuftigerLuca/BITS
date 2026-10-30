import { useState, type FormEvent } from 'react'
import { PlusIcon, Trash2Icon } from 'lucide-react'
import { ErrorState, LoadingState } from '@/components/QueryState'
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
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import type { Dsk, MilitaryUnits } from '@/lib/api'
import { errorMessage } from './labels'
import {
  useAddMilitaryUnit,
  useCreateDsk,
  useDeleteDsk,
  useMilitaryUnitSearch,
  useRemoveMilitaryUnit,
  useUpdateDsk,
} from './queries'

/** Value of "Keine Rolle" in the role select, which knows no `null` item. */
const NO_ROLE = 'none'

/** The DSKs with their roles and military units, and the form for another one. */
export function DsksCard({ data }: { data: MilitaryUnits }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>DSKs</CardTitle>
        <CardDescription>
          Wer verknüpft ist und in einer MU einer DSK ist, bekommt die Rolle der DSK, alle anderen verlieren sie. BITS
          gleicht nach jeder Änderung und stündlich ab. Eine Rolle gehört entweder zu einer DSK oder hat Regeln unter
          „Rollen“.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {data.dsks.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine DSK angelegt.</p>
        ) : (
          data.dsks.map((dsk) => <DskSection key={dsk.id} dsk={dsk} data={data} />)
        )}
        <NewDskForm data={data} />
      </CardContent>
    </Card>
  )
}

/** Roles the DSK [dskId] may get, `null` for a new one: without rules, no status role and of no other DSK. */
function roleItems(data: MilitaryUnits, dskId: number | null) {
  return [
    { value: NO_ROLE, label: 'Keine Rolle', pinned: true },
    ...data.roles
      .filter((r) => !r.hasRule && !r.statusRole && (r.dskId === null || r.dskId === dskId))
      .map((r) => ({ value: r.id, label: r.name })),
  ]
}

function RoleSelect({
  id,
  data,
  dskId,
  value,
  onValueChange,
}: {
  id: string
  data: MilitaryUnits
  dskId: number | null
  value: string
  onValueChange: (roleId: string) => void
}) {
  const colors = new Map(data.roles.map((r) => [r.id, r.color]))
  return (
    <SearchableSelect
      id={id}
      className="w-full"
      items={roleItems(data, dskId)}
      value={value}
      onValueChange={(next) => onValueChange(next ?? NO_ROLE)}
      renderItem={(item) =>
        item.value === NO_ROLE ? item.label : <RoleName name={item.label} color={colors.get(item.value) ?? null} />
      }
    />
  )
}

function DskSection({ dsk, data }: { dsk: Dsk; data: MilitaryUnits }) {
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const deleteDsk = useDeleteDsk()
  const removeUnit = useRemoveMilitaryUnit()
  return (
    <section aria-label={dsk.name} className="flex flex-col gap-3 rounded-lg border p-4">
      {editing ? (
        <EditDskForm dsk={dsk} data={data} onDone={() => setEditing(false)} />
      ) : (
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="flex flex-col gap-1">
            <h3 className="font-heading font-semibold">{dsk.name}</h3>
            <div className="flex flex-wrap items-center gap-2 text-sm">
              {dsk.roleId === null ? (
                <span className="text-muted-foreground">Keine Rolle, die DSK bewirkt nichts.</span>
              ) : (
                <RoleName name={dsk.roleName ?? `${dsk.roleId} (gelöscht)`} color={dsk.roleColor} />
              )}
              {dsk.roleStatus === 'ORPHANED' && <Badge variant="destructive">In Discord gelöscht</Badge>}
              {dsk.roleStatus === 'NOT_ASSIGNABLE' && <Badge variant="destructive">Bot kann sie nicht vergeben</Badge>}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              Bearbeiten
            </Button>
            <Button variant="outline" size="sm" disabled={deleteDsk.isPending} onClick={() => setDeleting(true)}>
              Löschen
            </Button>
          </div>
        </div>
      )}
      <div className="flex flex-col gap-2">
        <h4 className="text-sm font-medium">Military Units</h4>
        {dsk.militaryUnits.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine MU zugeordnet.</p>
        ) : (
          <ul className="flex flex-col gap-1">
            {dsk.militaryUnits.map((mu) => (
              <li key={mu.id} className="flex items-center gap-2 text-sm">
                <span>{mu.name}</span>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`${mu.name} aus ${dsk.name} entfernen`}
                  disabled={removeUnit.isPending}
                  onClick={() => removeUnit.mutate({ dskId: dsk.id, muId: mu.id })}
                >
                  <Trash2Icon />
                </Button>
              </li>
            ))}
          </ul>
        )}
        <MilitaryUnitSearch dsk={dsk} />
      </div>
      <AlertDialog open={deleting} onOpenChange={setDeleting}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{dsk.name} löschen?</AlertDialogTitle>
            <AlertDialogDescription>
              {dsk.roleName
                ? `Die Zuordnungen der MUs gehen verloren, und BITS entzieht allen die Rolle @${dsk.roleName}.`
                : 'Die Zuordnungen der MUs gehen verloren.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => deleteDsk.mutate(dsk.id)}>
              Löschen
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}

function EditDskForm({ dsk, data, onDone }: { dsk: Dsk; data: MilitaryUnits; onDone: () => void }) {
  const [name, setName] = useState(dsk.name)
  const [roleId, setRoleId] = useState(dsk.roleId ?? NO_ROLE)
  const update = useUpdateDsk()

  function submit(event: FormEvent) {
    event.preventDefault()
    update.mutate(
      { id: dsk.id, dsk: { name: name.trim(), roleId: roleId === NO_ROLE ? null : roleId } },
      { onSuccess: onDone },
    )
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      <DskFields
        idPrefix={`dsk-${dsk.id}`}
        data={data}
        dskId={dsk.id}
        name={name}
        onNameChange={setName}
        roleId={roleId}
        onRoleChange={setRoleId}
      />
      <div className="flex gap-2">
        <Button type="submit" disabled={!name.trim() || update.isPending}>
          Speichern
        </Button>
        <Button type="button" variant="outline" onClick={onDone}>
          Abbrechen
        </Button>
      </div>
    </form>
  )
}

function DskFields({
  idPrefix,
  data,
  dskId,
  name,
  onNameChange,
  roleId,
  onRoleChange,
}: {
  idPrefix: string
  data: MilitaryUnits
  dskId: number | null
  name: string
  onNameChange: (name: string) => void
  roleId: string
  onRoleChange: (roleId: string) => void
}) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row">
      <Field className="flex-1">
        <FieldLabel htmlFor={`${idPrefix}-name`}>Name</FieldLabel>
        <Input
          id={`${idPrefix}-name`}
          value={name}
          maxLength={100}
          placeholder="z. B. DSK1"
          onChange={(e) => onNameChange(e.target.value)}
        />
      </Field>
      <Field className="flex-1">
        <FieldLabel htmlFor={`${idPrefix}-role`}>Rolle</FieldLabel>
        <RoleSelect id={`${idPrefix}-role`} data={data} dskId={dskId} value={roleId} onValueChange={onRoleChange} />
      </Field>
    </div>
  )
}

/** Searches the military units of all countries by name and assigns one to the DSK. */
function MilitaryUnitSearch({ dsk }: { dsk: Dsk }) {
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState<string | null>(null)
  const search = useMilitaryUnitSearch(submitted)
  const addUnit = useAddMilitaryUnit()
  const inputId = `dsk-${dsk.id}-mu`

  function submit() {
    if (query.trim()) setSubmitted(query.trim())
  }

  return (
    <Field className="sm:max-w-sm">
      <FieldLabel htmlFor={inputId}>MU hinzufügen</FieldLabel>
      <div className="flex gap-2">
        <Input
          id={inputId}
          value={query}
          placeholder="Name einer MU"
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              submit()
            }
          }}
        />
        <Button type="button" variant="outline" disabled={!query.trim()} onClick={submit}>
          Suchen
        </Button>
      </div>
      {search.isFetching && <LoadingState label="MUs werden gesucht …" />}
      {search.isError && <ErrorState message={errorMessage(search.error)} onRetry={() => search.refetch()} />}
      {search.data && !search.isFetching && search.data.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Keine MU gefunden. WarEra liefert nur die besten Treffer, versuche einen genaueren Namen.
        </p>
      )}
      {search.data && !search.isFetching && search.data.length > 0 && (
        <ul className="flex flex-col gap-1">
          {search.data.map((mu) => (
            <li key={mu.id} className="flex items-center gap-2 text-sm">
              <span className="flex-1 truncate">
                {mu.name}
                {mu.countryName && <span className="text-muted-foreground"> · {mu.countryName}</span>}
                {mu.dskName && <span className="text-muted-foreground"> ({mu.dskName})</span>}
              </span>
              <Button
                variant="outline"
                size="sm"
                aria-label={`${mu.name} zu ${dsk.name} hinzufügen`}
                disabled={mu.dskName !== null || addUnit.isPending}
                onClick={() =>
                  addUnit.mutate(
                    { dskId: dsk.id, muId: mu.id },
                    {
                      onSuccess: () => {
                        setQuery('')
                        setSubmitted(null)
                      },
                    },
                  )
                }
              >
                <PlusIcon />
                Hinzufügen
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Field>
  )
}

function NewDskForm({ data }: { data: MilitaryUnits }) {
  const [name, setName] = useState('')
  const [roleId, setRoleId] = useState(NO_ROLE)
  const create = useCreateDsk()

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(
      { name: name.trim(), roleId: roleId === NO_ROLE ? null : roleId },
      {
        onSuccess: () => {
          setName('')
          setRoleId(NO_ROLE)
        },
      },
    )
  }

  return (
    <form className="flex flex-col gap-4 border-t pt-4" onSubmit={submit}>
      <h3 className="font-heading font-semibold">DSK anlegen</h3>
      <DskFields
        idPrefix="new-dsk"
        data={data}
        dskId={null}
        name={name}
        onNameChange={setName}
        roleId={roleId}
        onRoleChange={setRoleId}
      />
      <Button type="submit" className="self-start" disabled={!name.trim() || create.isPending}>
        DSK anlegen
      </Button>
    </form>
  )
}
