import { useState, type FormEvent } from 'react'
import { CopyIcon } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { notify } from '@/lib/notify'
import type { ApiScope, CreatedApiToken } from './api'
import { DEFAULT_DAYS, isoDay, MAX_DAYS } from './labels'
import { useCreateApiToken } from './queries'

/** The form for a new token, and the token itself once created, which is shown this once only. */
export function NewApiTokenCard({ scopes }: { scopes: ApiScope[] }) {
  const [created, setCreated] = useState<CreatedApiToken | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Token anlegen</CardTitle>
        <CardDescription>
          Gib jedem Tool ein eigenes Token mit nur den Scopes, die es braucht. Ein Token gilt höchstens ein Jahr.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {created ? (
          <CreatedToken created={created} onDone={() => setCreated(null)} />
        ) : (
          <NewApiTokenForm scopes={scopes} onCreated={setCreated} />
        )}
      </CardContent>
    </Card>
  )
}

function NewApiTokenForm({ scopes, onCreated }: { scopes: ApiScope[]; onCreated: (created: CreatedApiToken) => void }) {
  const [name, setName] = useState('')
  const [expiresOn, setExpiresOn] = useState(() => isoDay(DEFAULT_DAYS))
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const create = useCreateApiToken()
  const min = isoDay(1)
  const max = isoDay(MAX_DAYS)

  function toggle(id: string, checked: boolean) {
    const next = new Set(selected)
    if (checked) next.add(id)
    else next.delete(id)
    setSelected(next)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(
      // In the order of the documentation, not of the clicks.
      { name: name.trim(), expiresOn, scopes: scopes.map((scope) => scope.id).filter((id) => selected.has(id)) },
      { onSuccess: onCreated },
    )
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      <Field className="sm:max-w-sm">
        <FieldLabel htmlFor="api-token-name">Name</FieldLabel>
        <Input
          id="api-token-name"
          value={name}
          maxLength={100}
          aria-describedby="api-token-name-description"
          onChange={(event) => setName(event.target.value)}
        />
        <FieldDescription id="api-token-name-description">Wofür das Token ist, z. B. „Statistik-Bot“.</FieldDescription>
      </Field>
      <Field className="sm:max-w-sm">
        <FieldLabel htmlFor="api-token-expires">Gültig bis</FieldLabel>
        <Input
          id="api-token-expires"
          type="date"
          value={expiresOn}
          min={min}
          max={max}
          required
          aria-describedby="api-token-expires-description"
          onChange={(event) => setExpiresOn(event.target.value)}
        />
        <FieldDescription id="api-token-expires-description">
          Einschließlich dieses Tages, höchstens ein Jahr ab heute.
        </FieldDescription>
      </Field>
      <FieldSet>
        <FieldLegend variant="label">Scopes</FieldLegend>
        <FieldDescription>Jeder Scope erlaubt genau einen Endpunkt, immer nur lesend.</FieldDescription>
        <FieldGroup data-slot="checkbox-group" className="gap-3">
          {scopes.map((scope) => (
            <Field key={scope.id} orientation="horizontal">
              <Checkbox
                id={`api-token-scope-${scope.id}`}
                checked={selected.has(scope.id)}
                aria-describedby={`api-token-scope-${scope.id}-path`}
                onCheckedChange={(checked) => toggle(scope.id, checked)}
              />
              <div className="flex flex-col gap-0.5">
                <FieldLabel htmlFor={`api-token-scope-${scope.id}`} className="font-normal">
                  {scope.summary}
                </FieldLabel>
                <FieldDescription id={`api-token-scope-${scope.id}-path`}>
                  <code>{scope.id}</code> · GET {scope.path}
                </FieldDescription>
              </div>
            </Field>
          ))}
        </FieldGroup>
      </FieldSet>
      <Button
        type="submit"
        className="self-start"
        disabled={!name.trim() || !expiresOn || selected.size === 0 || create.isPending}
      >
        Token anlegen
      </Button>
    </form>
  )
}

function CreatedToken({ created, onDone }: { created: CreatedApiToken; onDone: () => void }) {
  async function copy() {
    try {
      await navigator.clipboard.writeText(created.secret)
      notify.success('Token kopiert')
    } catch {
      notify.error('Kopieren nicht möglich', 'Markiere das Token und kopiere es von Hand.')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Alert variant="warning" role="status">
        <AlertTitle>Token „{created.token.name}“ angelegt</AlertTitle>
        <AlertDescription>
          Kopiere es jetzt und gib es nur an das Tool weiter, für das es gedacht ist. BITS zeigt es nie wieder an.
        </AlertDescription>
      </Alert>
      <Field>
        <FieldLabel htmlFor="api-token-secret">Token</FieldLabel>
        <div className="flex gap-2">
          <Input
            id="api-token-secret"
            readOnly
            value={created.secret}
            className="font-mono"
            onFocus={(event) => event.target.select()}
          />
          <Button type="button" variant="outline" onClick={copy}>
            <CopyIcon aria-hidden />
            Kopieren
          </Button>
        </div>
      </Field>
      <Button type="button" variant="outline" size="sm" className="self-start" onClick={onDone}>
        Fertig
      </Button>
    </div>
  )
}
