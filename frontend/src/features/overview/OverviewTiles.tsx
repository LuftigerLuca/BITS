import { BadgeCheckIcon, FlagIcon, type LucideIcon, UsersIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Card, CardContent, CardTitle } from '@/components/ui/card'
import type { CountryOverview } from './api'
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
    <section aria-label="Kennzahlen" className="grid gap-4 lg:grid-cols-3">
      <Tile icon={UsersIcon} title="Discord-Mitglieder" value={members} note={members === null ? unknown : 'ohne Bots'} />
      <Tile
        icon={BadgeCheckIcon}
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
      <Tile icon={FlagIcon} title="Bürger" value={null} placeholder="kein Land">
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
        icon={FlagIcon}
        title={`Bürger von ${country.name}`}
        value={null}
        placeholder="nicht verfügbar"
        note="WarEra ist gerade nicht erreichbar."
      />
    )
  }
  return (
    <Tile
      icon={FlagIcon}
      title={`Bürger von ${country.name}`}
      value={country.citizens}
      note={country.activeCitizens === null ? undefined : `davon aktiv: ${formatCount(country.activeCitizens)}`}
    />
  )
}

function Tile({
  icon: Icon,
  title,
  value,
  placeholder = 'unbekannt',
  note,
  children,
}: {
  icon: LucideIcon
  title: string
  value: number | null
  placeholder?: string
  note?: string
  children?: ReactNode
}) {
  return (
    <Card>
      <CardContent className="flex items-start gap-4">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
          <Icon aria-hidden="true" className="size-5" />
        </div>
        <div className="grid min-w-0 gap-1">
          <h2 className="text-sm text-muted-foreground">{title}</h2>
          <CardTitle as="p" className={value === null ? 'text-2xl text-muted-foreground' : 'text-4xl tabular-nums'}>
            {value === null ? placeholder : formatCount(value)}
          </CardTitle>
          {(note || children) && <p className="text-sm text-muted-foreground">{note ?? children}</p>}
        </div>
      </CardContent>
    </Card>
  )
}
