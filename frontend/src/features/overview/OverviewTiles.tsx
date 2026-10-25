import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { CountryOverview } from '@/lib/api'
import { ERROR_MESSAGE, formatCount } from './labels'
import { useOverview } from './queries'

/** Key figures for admins: the Discord, how much of it is verified, and the citizens of the chosen country. */
export function OverviewTiles() {
  const overview = useOverview()

  if (overview.isPending) return <LoadingState label="Kennzahlen werden geladen …" />
  if (overview.isError) return <ErrorState message={ERROR_MESSAGE} onRetry={() => overview.refetch()} />

  const { members, verifiedMembers, country } = overview.data
  // Until the bot reports the members, 0 would be wrong.
  const unknown = 'Der Bot hat die Mitglieder noch nicht gemeldet.'
  return (
    <section aria-label="Kennzahlen" className="grid gap-4 sm:grid-cols-3">
      <Tile title="Discord-Mitglieder" value={members} note={members === null ? unknown : 'ohne Bots'} />
      <Tile
        title="davon verifiziert"
        value={verifiedMembers}
        note={
          verifiedMembers === null
            ? unknown
            : members
              ? `${Math.round((verifiedMembers / members) * 100)} % mit WarEra-Account`
              : 'mit WarEra-Account'
        }
      />
      <CitizensTile country={country} />
    </section>
  )
}

function CitizensTile({ country }: { country: CountryOverview | null }) {
  if (!country) {
    return (
      <Tile title="Bürger" value={null} placeholder="kein Land">
        Noch kein Land eingestellt.{' '}
        <Link to="/admin" className="underline underline-offset-4">
          In der Verwaltung einstellen
        </Link>
      </Tile>
    )
  }
  if (!country.available || country.citizens === null) {
    return (
      <Tile
        title={`Bürger von ${country.name}`}
        value={null}
        placeholder="nicht verfügbar"
        note="WarEra ist gerade nicht erreichbar."
      />
    )
  }
  return (
    <Tile
      title={`Bürger von ${country.name}`}
      value={country.citizens}
      note={country.activeCitizens === null ? undefined : `davon aktiv: ${formatCount(country.activeCitizens)}`}
    />
  )
}

function Tile({
  title,
  value,
  placeholder = 'unbekannt',
  note,
  children,
}: {
  title: string
  value: number | null
  placeholder?: string
  note?: string
  children?: ReactNode
}) {
  return (
    <Card size="sm">
      <CardHeader>
        <CardDescription>{title}</CardDescription>
        <CardTitle className={value === null ? 'text-xl text-muted-foreground' : 'text-3xl tabular-nums'}>
          {value === null ? placeholder : formatCount(value)}
        </CardTitle>
      </CardHeader>
      {(note || children) && <CardContent className="text-sm text-muted-foreground">{note ?? children}</CardContent>}
    </Card>
  )
}
