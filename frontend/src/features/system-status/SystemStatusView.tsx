import { ErrorState, LoadingState } from '@/components/QueryState'
import { formatTime } from '@/features/system-log/labels'
import { BotBackendCard } from './BotBackendCard'
import { Summary, ComponentCard } from './ComponentCards'
import { DiscordCard } from './DiscordCard'
import { JobsCard } from './JobsCard'
import { ERROR_MESSAGE } from './labels'
import { useSystemStatus } from './queries'
import { ResourcesCard } from './ResourcesCard'
import { WareraCard } from './WareraCard'

/** Whether the parts of BITS run, and how much they are used, as far as the backend knows. */
export function SystemStatusView() {
  const status = useSystemStatus()

  if (status.isPending) return <LoadingState label="System-Status wird geladen …" />
  if (status.isError) return <ErrorState message={ERROR_MESSAGE} onRetry={() => status.refetch()} />

  const data = status.data
  const now = data.generatedAt
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">
        Stand {formatTime(now)}, aktualisiert sich alle 15 Sekunden. Zähler seit dem Start des Backends{' '}
        {formatTime(data.backend.startedAt)}, Verläufe über die letzten 24 Stunden.
      </p>
      <Summary components={data.components} />
      <section aria-label="Komponenten" className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {data.components.map((component) => (
          <ComponentCard key={component.component} status={component} now={now} />
        ))}
      </section>
      <WareraCard data={data} />
      <BotBackendCard data={data} />
      <DiscordCard data={data} now={now} />
      <JobsCard data={data} now={now} />
      <ResourcesCard data={data} now={now} />
    </div>
  )
}
