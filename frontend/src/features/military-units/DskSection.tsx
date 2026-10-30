import { useState, type FormEvent } from 'react'
import { PlusIcon, Trash2Icon } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IconAction } from '@/components/IconAction'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { RoleName } from '@/components/RoleName'
import { Subsection } from '@/components/Subsection'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import type { Dsk, MilitaryUnits } from './api'
import { NO_ROLE, DskFields } from './DskFields'
import { errorMessage } from './labels'
import { useAddMilitaryUnit, useDeleteDsk, useMilitaryUnitSearch, useRemoveMilitaryUnit, useUpdateDsk } from './queries'

export function DskSection({ dsk, data }: { dsk: Dsk; data: MilitaryUnits }) {
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [unitToRemove, setUnitToRemove] = useState<Dsk['militaryUnits'][number] | null>(null)
  const deleteDsk = useDeleteDsk()
  const removeUnit = useRemoveMilitaryUnit()
  return (
    <Subsection
      title={dsk.name}
      description={
        editing ? undefined : (
          <span className="flex flex-wrap items-center gap-2">
            {dsk.roleId === null ? (
              'Keine Rolle, die DSK bewirkt nichts.'
            ) : (
              <RoleName name={dsk.roleName ?? `${dsk.roleId} (gelöscht)`} color={dsk.roleColor} />
            )}
            {dsk.roleStatus === 'ORPHANED' && <Badge variant="destructive">In Discord gelöscht</Badge>}
            {dsk.roleStatus === 'NOT_ASSIGNABLE' && <Badge variant="destructive">Bot kann sie nicht vergeben</Badge>}
          </span>
        )
      }
      actions={
        !editing && (
          <>
            <Button variant="outline" size="sm" aria-label={`${dsk.name} bearbeiten`} onClick={() => setEditing(true)}>
              Bearbeiten
            </Button>
            <Button
              variant="outline"
              size="sm"
              aria-label={`${dsk.name} löschen`}
              disabled={deleteDsk.isPending}
              onClick={() => setDeleting(true)}
            >
              Löschen
            </Button>
          </>
        )
      }
    >
      {editing && <EditDskForm dsk={dsk} data={data} onDone={() => setEditing(false)} />}
      <div className="flex flex-col gap-2">
        <h4 className="text-sm font-medium">Military Units</h4>
        {dsk.militaryUnits.length === 0 ? (
          <p className="text-sm text-muted-foreground">Noch keine MU zugeordnet.</p>
        ) : (
          <ul className="flex flex-col gap-1">
            {dsk.militaryUnits.map((mu) => (
              <li key={mu.id} className="flex items-center gap-2 text-sm">
                <span>{mu.name}</span>
                <IconAction
                  label={`${mu.name} aus ${dsk.name} entfernen`}
                  disabled={removeUnit.isPending}
                  onClick={() => setUnitToRemove(mu)}
                >
                  <Trash2Icon />
                </IconAction>
              </li>
            ))}
          </ul>
        )}
        <MilitaryUnitSearch dsk={dsk} />
      </div>
      <ConfirmDialog
        open={deleting}
        title={`${dsk.name} löschen?`}
        description={
          dsk.roleName
            ? `Die Zuordnungen der MUs gehen verloren, und BITS entzieht allen die Rolle @${dsk.roleName}.`
            : 'Die Zuordnungen der MUs gehen verloren.'
        }
        confirm="Löschen"
        onConfirm={() => {
          setDeleting(false)
          deleteDsk.mutate(dsk.id)
        }}
        onCancel={() => setDeleting(false)}
      />
      <ConfirmDialog
        open={unitToRemove !== null}
        title={`${unitToRemove?.name ?? 'MU'} aus ${dsk.name} entfernen?`}
        description={
          dsk.roleName
            ? `BITS entzieht den Mitgliedern der MU die Rolle @${dsk.roleName}, außer sie sind in einer anderen MU der DSK.`
            : 'Die DSK hat keine Rolle, niemand verliert etwas.'
        }
        confirm="Entfernen"
        onConfirm={() => {
          if (unitToRemove) removeUnit.mutate({ dskId: dsk.id, muId: unitToRemove.id })
          setUnitToRemove(null)
        }}
        onCancel={() => setUnitToRemove(null)}
      />
    </Subsection>
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
              <IconAction
                label={`${mu.name} zu ${dsk.name} hinzufügen`}
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
              </IconAction>
            </li>
          ))}
        </ul>
      )}
    </Field>
  )
}
