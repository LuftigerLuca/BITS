import { useState, type FormEvent } from 'react'
import { EmptyState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { MilitaryUnits } from './api'
import { NO_ROLE, DskFields } from './DskFields'
import { DskSection } from './DskSection'
import { useCreateDsk } from './queries'

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
          <EmptyState title="Noch keine DSK angelegt" description="Lege unter „DSK anlegen“ die erste an." />
        ) : (
          data.dsks.map((dsk) => <DskSection key={dsk.id} dsk={dsk} data={data} />)
        )}
        <NewDskForm data={data} />
      </CardContent>
    </Card>
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
