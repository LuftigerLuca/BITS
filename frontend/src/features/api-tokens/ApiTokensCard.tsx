import { useState } from 'react'
import { CircleCheckIcon, CircleXIcon, Trash2Icon, TriangleAlertIcon } from 'lucide-react'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { IconAction } from '@/components/IconAction'
import { EmptyState } from '@/components/QueryState'
import { Subsection } from '@/components/Subsection'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { ApiToken, ApiTokens, ApiTokenStatus } from './api'
import { DOCS_URL, formatLastDay, formatTime, STATUS } from './labels'
import { useRevokeApiToken } from './queries'

const STATUS_ICONS: Record<ApiTokenStatus, typeof CircleCheckIcon> = {
  ACTIVE: CircleCheckIcon,
  EXPIRING: TriangleAlertIcon,
  EXPIRED: CircleXIcon,
}

/** The tokens of the third-party API, the newest first, each with the way to revoke it. */
export function ApiTokensCard({ data }: { data: ApiTokens }) {
  const [revoking, setRevoking] = useState<ApiToken | null>(null)
  const revoke = useRevokeApiToken()
  const summaries = new Map(data.scopes.map((scope) => [scope.id, scope.summary]))

  return (
    <Card>
      <CardHeader>
        <CardTitle>API-Tokens</CardTitle>
        <CardDescription>
          Mit einem Token lesen andere Tools Daten von BITS über die API, nie mehr als seine Scopes erlauben. Was die
          Endpunkte liefern, steht in der{' '}
          <a href={DOCS_URL} target="_blank" rel="noreferrer" className="underline underline-offset-4">
            API-Dokumentation
          </a>
          . Scopes und Ablaufdatum lassen sich nicht ändern: Lege dafür ein neues Token an und widerrufe das alte.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {data.tokens.length === 0 ? (
          <EmptyState title="Noch kein Token angelegt" description="Lege unter „Token anlegen“ das erste an." />
        ) : (
          data.tokens.map((token) => (
            <TokenSection
              key={token.id}
              token={token}
              summaries={summaries}
              disabled={revoke.isPending}
              onRevoke={() => setRevoking(token)}
            />
          ))
        )}
      </CardContent>
      <ConfirmDialog
        open={revoking !== null}
        title={`${revoking?.name ?? 'Token'} ${revoking?.status === 'EXPIRED' ? 'entfernen' : 'widerrufen'}?`}
        description={
          revoking?.status === 'EXPIRED'
            ? 'Das abgelaufene Token verschwindet aus der Liste.'
            : 'Das Token funktioniert ab sofort nicht mehr. Tools, die es nutzen, bekommen keine Daten mehr.'
        }
        confirm={revoking?.status === 'EXPIRED' ? 'Entfernen' : 'Widerrufen'}
        onConfirm={() => {
          if (revoking) revoke.mutate(revoking.id)
          setRevoking(null)
        }}
        onCancel={() => setRevoking(null)}
      />
    </Card>
  )
}

function TokenSection({
  token,
  summaries,
  disabled,
  onRevoke,
}: {
  token: ApiToken
  summaries: Map<string, string>
  disabled: boolean
  onRevoke: () => void
}) {
  const status = STATUS[token.status]
  const StatusIcon = STATUS_ICONS[token.status]
  const action = token.status === 'EXPIRED' ? 'entfernen' : 'widerrufen'
  return (
    <Subsection
      title={token.name}
      label={`Token ${token.name}`}
      description={
        <>
          <code>{token.hint}…</code> · gültig bis {formatLastDay(token.expiresAt)} · angelegt von {token.createdBy} am{' '}
          {formatTime(token.createdAt)} · {token.lastUsedAt ? `zuletzt benutzt ${formatTime(token.lastUsedAt)}` : 'nie benutzt'}
        </>
      }
      actions={
        <>
          <Badge variant={status.variant}>
            <StatusIcon aria-hidden />
            {status.label}
          </Badge>
          <IconAction label={`${token.name} ${action}`} disabled={disabled} onClick={onRevoke}>
            <Trash2Icon />
          </IconAction>
        </>
      }
    >
      <ul className="flex flex-col gap-1 text-sm" aria-label="Scopes">
        {token.scopes.map((scope) => (
          <li key={scope}>
            <code>{scope}</code>
            {summaries.has(scope) && <span className="text-muted-foreground"> – {summaries.get(scope)}</span>}
          </li>
        ))}
      </ul>
    </Subsection>
  )
}
