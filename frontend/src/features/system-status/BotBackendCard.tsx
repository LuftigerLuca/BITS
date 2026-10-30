import { ArrowLeftRightIcon } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatTime } from '@/features/system-log/labels'
import type { SystemStatus } from './api'
import { endpointName, formatCount } from './labels'
import { Count, Stat, History, Section, Empty } from './StatusParts'
import { nonZero, OUTBOX_STUCK_MILLIS, latency } from './status'

export function BotBackendCard({ data }: { data: SystemStatus }) {
  const { api, events, bot } = data
  return (
    <Section
      title="Bot ↔ Backend"
      icon={ArrowLeftRightIcon}
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
              <Count value={endpoint.clientErrors} tone={nonZero(endpoint.clientErrors)} />
              <Count value={endpoint.serverErrors} tone={nonZero(endpoint.serverErrors, 'critical')} />
              <TableCell className="text-right tabular-nums">{latency(endpoint.latency)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {bot && (
        <>
          <History
            label="Fehlgeschlagene Anfragen aus Sicht des Bots"
            buckets={bot.failureHistory}
            now={data.generatedAt}
            tone="critical"
          />
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
                  <Count value={calls.failed} tone={nonZero(calls.failed, 'critical')} />
                  <Count value={calls.timedOut} tone={nonZero(calls.timedOut)} />
                  <Count value={calls.unreachable} tone={nonZero(calls.unreachable, 'critical')} />
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
          tone={events.outboxOldestAt !== null && data.generatedAt - events.outboxOldestAt >= OUTBOX_STUCK_MILLIS ? 'warning' : undefined}
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
          tone={nonZero(events.deadLetters)}
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
                {group.lag === null ? (
                  <TableCell className="text-right">unbekannt</TableCell>
                ) : (
                  <Count value={group.lag} tone={nonZero(group.lag)} />
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </Section>
  )
}
