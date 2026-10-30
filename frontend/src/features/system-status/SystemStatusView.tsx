import { CircleCheckIcon, CircleHelpIcon, CircleXIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatTime } from '@/features/system-log/labels'
import type { ComponentStatus, LatencyStats, SystemStatus } from '@/lib/api'
import {
  CALLERS,
  COMPONENTS,
  ERROR_MESSAGE,
  JOBS,
  endpointName,
  formatAgo,
  formatBytes,
  formatCount,
  formatDuration,
  scheduledTaskName,
  triggerName,
} from './labels'
import { useSystemStatus } from './queries'

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

function UpBadge({ up }: { up: boolean | null }) {
  if (up === null) {
    return (
      <Badge variant="outline">
        <CircleHelpIcon aria-hidden />
        Unbekannt
      </Badge>
    )
  }
  return up ? (
    <Badge variant="secondary">
      <CircleCheckIcon aria-hidden />
      Erreichbar
    </Badge>
  ) : (
    <Badge variant="destructive">
      <CircleXIcon aria-hidden />
      Gestört
    </Badge>
  )
}

function ComponentCard({ status, now }: { status: ComponentStatus; now: number }) {
  const name = COMPONENTS[status.component]
  const contact = status.component === 'BOT' ? 'Letzter Heartbeat' : status.component === 'WARERA' ? 'Letzter Kontakt' : 'Geprüft'
  const duration = status.component === 'BOT' ? 'Gateway-Ping' : 'Antwortzeit'
  return (
    <Card size="sm" role="group" aria-label={name}>
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-2">
          {name}
          <UpBadge up={status.up} />
        </CardTitle>
        <CardDescription>
          {status.up === null
            ? status.component === 'WARERA'
              ? 'Seit dem Start noch kein Aufruf.'
              : 'Noch keine Rückmeldung.'
            : status.since !== null && `${status.up ? 'Erreichbar' : 'Gestört'} seit ${formatTime(status.since)}`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          {status.checkedAt !== null && <Fact label={contact}>{formatAgo(status.checkedAt, now)}</Fact>}
          {status.durationMillis !== null && <Fact label={duration}>{formatDuration(status.durationMillis)}</Fact>}
          {status.lastProblem !== null && (
            <Fact label="Letzte Störung">
              {status.lastFailureAt !== null && `${formatTime(status.lastFailureAt)}: `}
              {status.lastProblem}
            </Fact>
          )}
        </dl>
      </CardContent>
    </Card>
  )
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </>
  )
}

function Stat({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="flex flex-col">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="font-heading text-2xl font-semibold tabular-nums">{value}</span>
      {note && <span className="text-xs text-muted-foreground">{note}</span>}
    </div>
  )
}

const BUCKET_MILLIS = 15 * 60_000
const TIME_OF_DAY = new Intl.DateTimeFormat('de-DE', { timeStyle: 'short' })

/**
 * Counts per 15 minutes over the last 24 hours as bars, the newest on the right; each bar names its time and count
 * on hover. The tables next to it carry the same data in numbers.
 */
function History({ label, buckets, now }: { label: string; buckets: number[]; now: number }) {
  const max = Math.max(...buckets, 0)
  const total = buckets.reduce((sum, count) => sum + count, 0)
  return (
    <figure className="flex flex-col gap-1">
      <figcaption className="text-sm text-muted-foreground">
        {label}: {formatCount(total)} in 24 Stunden, höchstens {formatCount(max)} in 15 Minuten
      </figcaption>
      <div className="flex h-16 items-end gap-[2px] border-b" aria-hidden>
        {buckets.map((count, index) => {
          const end = now - (buckets.length - 1 - index) * BUCKET_MILLIS
          return (
            <div
              key={index}
              title={`${TIME_OF_DAY.format(end - BUCKET_MILLIS)}–${TIME_OF_DAY.format(end)}: ${formatCount(count)}`}
              className="flex h-full flex-1 items-end hover:bg-muted"
            >
              <div
                className="w-full rounded-t-[2px] bg-primary"
                style={{ height: count === 0 || max === 0 ? 0 : `${Math.max(4, (count / max) * 100)}%` }}
              />
            </div>
          )
        })}
      </div>
      <div className="flex justify-between text-xs text-muted-foreground" aria-hidden>
        <span>vor 24 h</span>
        <span>jetzt</span>
      </div>
    </figure>
  )
}

function latency(stats: LatencyStats | null): string {
  return stats ? `${formatDuration(stats.p50Millis)} / ${formatDuration(stats.p95Millis)}` : '–'
}

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <Card role="region" aria-label={title}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">{children}</CardContent>
    </Card>
  )
}

function Empty({ columns, children }: { columns: number; children: ReactNode }) {
  return (
    <TableRow>
      <TableCell colSpan={columns} className="text-muted-foreground">
        {children}
      </TableCell>
    </TableRow>
  )
}

function WareraCard({ data }: { data: SystemStatus }) {
  const warera = data.warera
  return (
    <Section
      title="WarEra-API"
      description={`Jeder Request zählt gegen das Limit von WarEra; BITS erlaubt sich ${warera.limitPerMinute} pro Minute, Wiederholungen eingeschlossen.`}
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Letzte Minute" value={`${formatCount(warera.lastMinute)} / ${warera.limitPerMinute}`} />
        <Stat label="Höchstens pro Minute" value={formatCount(warera.peakPerMinute)} note="in den letzten 24 Stunden" />
        <Stat label="Letzte Stunde" value={formatCount(warera.lastHour)} />
        <Stat
          label="Wartezeit auf das Limit"
          value={warera.permitWait ? formatDuration(warera.permitWait.p95Millis) : '–'}
          note="95 % der Requests warteten höchstens so lange"
        />
      </div>
      <History label="Requests an WarEra" buckets={warera.history} now={data.generatedAt} />
      <Table aria-label="WarEra-Aufrufe nach Prozedur">
        <TableHeader>
          <TableRow>
            <TableHead>Prozedur</TableHead>
            <TableHead className="text-right">Requests</TableHead>
            <TableHead className="text-right">Letzte Stunde</TableHead>
            <TableHead className="text-right">Fehler</TableHead>
            <TableHead className="text-right">429</TableHead>
            <TableHead className="text-right">Wiederholt</TableHead>
            <TableHead className="text-right">Gedrosselt</TableHead>
            <TableHead className="text-right">Aus dem Cache</TableHead>
            <TableHead className="text-right">Dauer p50 / p95</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {warera.procedures.length === 0 && <Empty columns={9}>Seit dem Start noch kein Aufruf.</Empty>}
          {warera.procedures.map((procedure) => {
            const lookups = procedure.cacheHits + procedure.cacheMisses
            return (
              <TableRow key={procedure.procedure}>
                <TableCell className="font-mono text-xs">{procedure.procedure}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(procedure.total)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(procedure.lastHour)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(procedure.failures)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(procedure.rateLimited)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(procedure.retries)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(procedure.throttled)}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {lookups === 0
                    ? '–'
                    : `${formatCount(procedure.cacheHits)} von ${formatCount(lookups)} (${Math.round((procedure.cacheHits / lookups) * 100)} %)`}
                </TableCell>
                <TableCell className="text-right tabular-nums">{latency(procedure.latency)}</TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
      <Table aria-label="WarEra-Aufrufe nach Auslöser">
        <TableHeader>
          <TableRow>
            <TableHead>Ausgelöst durch</TableHead>
            <TableHead className="text-right">Requests</TableHead>
            <TableHead className="text-right">Letzte Stunde</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {warera.callers.length === 0 && <Empty columns={3}>Seit dem Start noch kein Aufruf.</Empty>}
          {warera.callers.map((caller) => (
            <TableRow key={caller.caller}>
              <TableCell>{CALLERS[caller.caller]}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(caller.total)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(caller.lastHour)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Section>
  )
}

function BotBackendCard({ data }: { data: SystemStatus }) {
  const { api, events, bot } = data
  return (
    <Section
      title="Bot ↔ Backend"
      description="Der Bot ruft das Backend per REST auf, das Backend erreicht den Bot über Events im Redis-Stream."
    >
      <History label="Anfragen des Bots, im Backend gezählt" buckets={api.history} now={data.generatedAt} />
      <Table aria-label="Anfragen des Bots nach Endpunkt">
        <TableHeader>
          <TableRow>
            <TableHead>Endpunkt</TableHead>
            <TableHead className="text-right">Anfragen</TableHead>
            <TableHead className="text-right">Letzte Stunde</TableHead>
            <TableHead className="text-right">4xx</TableHead>
            <TableHead className="text-right">5xx</TableHead>
            <TableHead className="text-right">Dauer p50 / p95</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {api.endpoints.length === 0 && <Empty columns={6}>Seit dem Start noch keine Anfrage.</Empty>}
          {api.endpoints.map((endpoint) => (
            <TableRow key={endpoint.endpoint}>
              <TableCell className="font-mono text-xs">{endpointName(endpoint.endpoint)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(endpoint.total)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(endpoint.lastHour)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(endpoint.clientErrors)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(endpoint.serverErrors)}</TableCell>
              <TableCell className="text-right tabular-nums">{latency(endpoint.latency)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {bot && (
        <>
          <History label="Fehlgeschlagene Anfragen aus Sicht des Bots" buckets={bot.failureHistory} now={data.generatedAt} />
          <Table aria-label="Anfragen aus Sicht des Bots">
            <TableHeader>
              <TableRow>
                <TableHead>Endpunkt (seit Start des Bots)</TableHead>
                <TableHead className="text-right">Erfolgreich</TableHead>
                <TableHead className="text-right">Fehler</TableHead>
                <TableHead className="text-right">Zeitüberschreitung</TableHead>
                <TableHead className="text-right">Nicht erreichbar</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {bot.backendCalls.length === 0 && <Empty columns={5}>Noch keine Anfrage.</Empty>}
              {bot.backendCalls.map((calls) => (
                <TableRow key={calls.endpoint}>
                  <TableCell className="font-mono text-xs">{calls.endpoint}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(calls.succeeded)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(calls.failed)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(calls.timedOut)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(calls.unreachable)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </>
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="In der Outbox"
          value={events.outboxPending === null ? 'unbekannt' : formatCount(events.outboxPending)}
          note={events.outboxOldestAt === null ? 'Events, die noch nicht in Redis sind' : `ältestes von ${formatTime(events.outboxOldestAt)}`}
        />
        <Stat
          label="Im Stream"
          value={events.streamLength === null ? 'unbekannt' : formatCount(events.streamLength)}
          note="Events der letzten 7 Tage"
        />
        <Stat
          label="Aufgegeben"
          value={events.deadLetters === null ? 'unbekannt' : formatCount(events.deadLetters)}
          note="Events, die der Bot nicht verarbeiten konnte"
        />
        <Stat label="Veröffentlicht" value={formatCount(events.published.reduce((sum, type) => sum + type.total, 0))} note="seit dem Start" />
      </div>
      <History label="Events an den Bot" buckets={events.history} now={data.generatedAt} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Table aria-label="Events nach Typ">
          <TableHeader>
            <TableRow>
              <TableHead>Event</TableHead>
              <TableHead className="text-right">Veröffentlicht</TableHead>
              <TableHead className="text-right">Letzte Stunde</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {events.published.length === 0 && <Empty columns={3}>Seit dem Start noch kein Event.</Empty>}
            {events.published.map((type) => (
              <TableRow key={type.type}>
                <TableCell className="font-mono text-xs">{type.type}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(type.total)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(type.lastHour)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Table aria-label="Consumer-Groups des Bots">
          <TableHeader>
            <TableRow>
              <TableHead>Lane (Consumer-Group)</TableHead>
              <TableHead className="text-right">In Arbeit</TableHead>
              <TableHead className="text-right">Noch nicht abgeholt</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {events.groups === null && <Empty columns={3}>Redis konnte nicht gefragt werden.</Empty>}
            {events.groups?.length === 0 && <Empty columns={3}>Der Bot hat noch keine Lane angelegt.</Empty>}
            {events.groups?.map((group) => (
              <TableRow key={group.name}>
                <TableCell className="font-mono text-xs">{group.name}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(group.pending)}</TableCell>
                <TableCell className="text-right tabular-nums">{group.lag === null ? 'unbekannt' : formatCount(group.lag)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </Section>
  )
}

function DiscordCard({ data, now }: { data: SystemStatus; now: number }) {
  const bot = data.bot
  return (
    <Section title="Discord" description="Wie der Bot mit Discord verbunden ist, aus seinem letzten Heartbeat.">
      {!bot ? (
        <p className="text-sm text-muted-foreground">Seit dem Start des Backends kam noch kein Heartbeat vom Bot.</p>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Gateway" value={bot.gatewayStatus} />
            <Stat label="Gateway-Ping" value={bot.gatewayPingMillis < 0 ? 'unbekannt' : formatDuration(bot.gatewayPingMillis)} />
            <Stat label="Mitglieder" value={bot.guildMembers === null ? 'unbekannt' : formatCount(bot.guildMembers)} />
            <Stat
              label="REST-Anfragen an Discord"
              value={formatCount(bot.discord.total)}
              note={`seit Start des Bots ${formatAgo(bot.process.startedAt, now)}`}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat label="Rate-Limit (429)" value={formatCount(bot.discord.rateLimited)} note="Discord ließ den Bot warten" />
            <Stat label="Abgelehnt" value={formatCount(bot.discord.refused)} note="z. B. fehlende Rechte für eine Rolle" />
            <Stat label="Serverfehler" value={formatCount(bot.discord.serverErrors)} />
          </div>
          <History label="REST-Anfragen und Fehler bei Discord" buckets={bot.discordHistory} now={data.generatedAt} />
        </>
      )}
    </Section>
  )
}

function JobsCard({ data, now }: { data: SystemStatus; now: number }) {
  return (
    <Section title="Hintergrund-Jobs" description="Was BITS von sich aus tut, seit dem Start des Backends.">
      <Table aria-label="Hintergrund-Jobs">
        <TableHeader>
          <TableRow>
            <TableHead>Job</TableHead>
            <TableHead>Letzter Lauf</TableHead>
            <TableHead className="text-right">Dauer</TableHead>
            <TableHead>Ergebnis</TableHead>
            <TableHead className="text-right">Läufe / Fehler</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.jobs.map((job) => {
            const trigger = triggerName(job.runningSince !== null ? job.runningTrigger : job.lastTrigger)
            return (
              <TableRow key={job.job}>
                <TableCell>{JOBS[job.job]}</TableCell>
                <TableCell>
                  {job.runningSince !== null
                    ? `läuft seit ${formatAgo(job.runningSince, now).replace(/^vor /, '')}`
                    : job.lastStartedAt === null
                      ? 'noch nicht'
                      : formatTime(job.lastStartedAt)}
                  {trigger && <span className="text-muted-foreground"> ({trigger})</span>}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {job.lastDurationMillis === null ? '–' : formatDuration(job.lastDurationMillis)}
                </TableCell>
                <TableCell>
                  {job.lastStartedAt === null ? (
                    '–'
                  ) : job.lastFailure === null ? (
                    <Badge variant="secondary">
                      <CircleCheckIcon aria-hidden />
                      OK
                    </Badge>
                  ) : (
                    <span className="flex flex-col items-start gap-1">
                      <Badge variant="destructive">
                        <CircleXIcon aria-hidden />
                        Fehler
                      </Badge>
                      <span className="text-xs text-muted-foreground">{job.lastFailure}</span>
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatCount(job.runs)} / {formatCount(job.failures)}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
      <Table aria-label="Geplante Jobs">
        <TableHeader>
          <TableRow>
            <TableHead>Geplant</TableHead>
            <TableHead>Zuletzt</TableHead>
            <TableHead>Als Nächstes</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.scheduledTasks.length === 0 && <Empty columns={3}>Keine geplanten Jobs.</Empty>}
          {data.scheduledTasks.map((task) => (
            <TableRow key={task.name}>
              <TableCell>{scheduledTaskName(task.name)}</TableCell>
              <TableCell>
                {task.lastAt === null ? 'noch nicht' : formatTime(task.lastAt)}
                {task.lastOutcome === 'ERROR' && <span className="text-destructive"> (fehlgeschlagen)</span>}
                {task.lastOutcome === 'STARTED' && <span className="text-muted-foreground"> (läuft)</span>}
              </TableCell>
              <TableCell>{task.nextAt === null ? '–' : formatTime(task.nextAt)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Section>
  )
}

function ResourcesCard({ data, now }: { data: SystemStatus; now: number }) {
  const { backend, bot, postgres, redis } = data
  const processes = [{ name: 'Backend', process: backend }, ...(bot ? [{ name: 'Bot', process: bot.process }] : [])]
  const mismatch = bot && bot.process.version !== backend.version
  return (
    <Section title="Versionen und Ressourcen" description="Was gerade läuft und wie viel Speicher es braucht.">
      {mismatch && (
        <p className="text-sm">
          Backend und Bot laufen mit verschiedenen Versionen; nach einem Deploy sollte das nur kurz so sein.
        </p>
      )}
      <Table aria-label="Prozesse">
        <TableHeader>
          <TableRow>
            <TableHead>Prozess</TableHead>
            <TableHead>Version</TableHead>
            <TableHead>Gestartet</TableHead>
            <TableHead className="text-right">Heap</TableHead>
            <TableHead className="text-right">Threads</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {processes.map(({ name, process }) => (
            <TableRow key={name}>
              <TableCell>{name}</TableCell>
              <TableCell className="font-mono text-xs">{process.version.slice(0, 12)}</TableCell>
              <TableCell>
                {formatTime(process.startedAt)} <span className="text-muted-foreground">({formatAgo(process.startedAt, now)})</span>
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatBytes(process.heapUsedBytes)} von {formatBytes(process.heapMaxBytes)}
              </TableCell>
              <TableCell className="text-right tabular-nums">{formatCount(process.threads)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <Fact label="Datenbank">{postgres ? formatBytes(postgres.sizeBytes) : 'unbekannt'}</Fact>
        <Fact label="Schema-Version">{postgres?.schemaVersion ?? 'unbekannt'}</Fact>
        <Fact label="Verbindungen">
          {postgres?.poolMax == null
            ? 'unbekannt'
            : `${postgres.poolActive ?? 0} aktiv, ${postgres.poolIdle ?? 0} frei, ${postgres.poolWaiting ?? 0} wartend, höchstens ${postgres.poolMax}`}
        </Fact>
        <Fact label="Redis-Speicher">
          {redis
            ? `${formatBytes(redis.usedMemoryBytes)}${redis.maxMemoryBytes === null ? ', ohne Limit' : ` von ${formatBytes(redis.maxMemoryBytes)}`}`
            : 'unbekannt'}
        </Fact>
      </dl>
    </Section>
  )
}
