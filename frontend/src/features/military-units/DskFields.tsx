import { RoleName } from '@/components/RoleName'
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import { SearchableSelect } from '@/components/ui/searchable-select'
import type { MilitaryUnits } from './api'

/** Value of "Keine Rolle" in the role select, which knows no `null` item. */
export const NO_ROLE = 'none'

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

export function DskFields({
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
