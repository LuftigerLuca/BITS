import type { UseQueryResult } from '@tanstack/react-query'
import { ChevronRightIcon } from 'lucide-react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import type { PreviewMember, RulePreview as RulePreviewData } from './api'
import { errorMessage } from './labels'

/** Whom a rule change gives the role and from whom it takes it, shown before the admin confirms it. */
export function RulePreview({ preview }: { preview: UseQueryResult<RulePreviewData> }) {
  if (preview.isPending) return <LoadingState label="Auswirkung wird berechnet …" />
  if (preview.isError) return <ErrorState message={errorMessage(preview.error)} onRetry={() => preview.refetch()} />
  const { managed, membersReported, usersLoaded, gains, losses, unknown } = preview.data
  if (!managed) {
    return (
      <p className="text-sm text-muted-foreground">
        Danach gibt es keine Regel mehr für die Rolle. BITS verwaltet sie nicht mehr; niemand bekommt oder verliert sie.
      </p>
    )
  }
  if (!membersReported) {
    return (
      <Alert>
        <AlertDescription>
          Der Bot hat die Mitglieder seit dem Neustart des Backends noch nicht gemeldet, daher gibt es noch keine
          Vorschau.
        </AlertDescription>
      </Alert>
    )
  }
  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="font-medium">{summary(gains.length, losses.length)}</p>
      {!usersLoaded && (
        <Alert>
          <AlertDescription>
            Seit dem Neustart des Backends sind noch nicht alle WarEra-Daten geladen. Bis zum nächsten Abgleich ist die
            Vorschau ungenau.
          </AlertDescription>
        </Alert>
      )}
      {unknown.length > 0 && (
        <p className="text-muted-foreground">
          {members(unknown.length, 'nominative')} ohne WarEra-Daten aus dem letzten Abgleich{' '}
          {unknown.length === 1 ? 'ist' : 'sind'} unbekannt und nicht mitgezählt.
        </p>
      )}
      <MemberList title="Bekommen die Rolle" members={gains} />
      <MemberList title="Verlieren die Rolle" members={losses} />
      <MemberList title="Unbekannt" members={unknown} />
    </div>
  )
}

function summary(gains: number, losses: number): string {
  if (gains === 0 && losses === 0) return 'Ändert für kein Mitglied etwas.'
  if (losses === 0) return `Gibt ${members(gains, 'dative')} die Rolle.`
  if (gains === 0) return `Nimmt ${members(losses, 'dative')} die Rolle weg.`
  return `Gibt ${members(gains, 'dative')} die Rolle, nimmt sie ${losses} weg.`
}

function members(count: number, grammaticalCase: 'nominative' | 'dative'): string {
  if (count === 1) return '1 Mitglied'
  return `${count} ${grammaticalCase === 'dative' ? 'Mitgliedern' : 'Mitglieder'}`
}

function MemberList({ title, members }: { title: string; members: PreviewMember[] }) {
  if (members.length === 0) return null
  return (
    <Collapsible>
      <CollapsibleTrigger render={<Button variant="ghost" size="sm" className="group -ml-2.5" />}>
        <ChevronRightIcon className="transition-transform group-data-[panel-open]:rotate-90" aria-hidden />
        {title} ({members.length})
      </CollapsibleTrigger>
      <CollapsibleContent>
        <ul className="max-h-40 overflow-y-auto pl-6 text-muted-foreground">
          {members.map((member) => (
            <li key={member.discordUserId}>{member.name ?? `Unbekanntes Mitglied (${member.discordUserId})`}</li>
          ))}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  )
}
