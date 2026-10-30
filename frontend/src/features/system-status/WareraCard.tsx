import { GlobeIcon } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { SystemStatus } from './api'
import { CALLERS, formatCount, formatDuration } from './labels'
import { Count, Stat, History, Section, Empty } from './StatusParts'
import { nonZero, share, LIMIT_WARNING, SLOW_MILLIS, latency } from './status'

export function WareraCard({ data }: { data: SystemStatus }) {
  const warera = data.warera
  return (
    <Section
      title="WarEra-API"
      icon={GlobeIcon}
      description={`Jeder Request zählt gegen das Limit von WarEra; BITS erlaubt sich ${warera.limitPerMinute} pro Minute, Wiederholungen eingeschlossen.`}
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="Letzte Minute"
          value={`${formatCount(warera.lastMinute)} / ${warera.limitPerMinute}`}
          tone={share(warera.lastMinute, warera.limitPerMinute, LIMIT_WARNING)}
          meter={warera.lastMinute / warera.limitPerMinute}
        />
        <Stat
          label="Höchstens pro Minute"
          value={formatCount(warera.peakPerMinute)}
          note="in den letzten 24 Stunden"
          tone={share(warera.peakPerMinute, warera.limitPerMinute, LIMIT_WARNING)}
          meter={warera.peakPerMinute / warera.limitPerMinute}
        />
        <Stat label="Letzte Stunde" value={formatCount(warera.lastHour)} />
        <Stat
          label="Wartezeit auf das Limit"
          value={warera.permitWait ? formatDuration(warera.permitWait.p95Millis) : '–'}
          note="95 % der Requests warteten höchstens so lange"
          tone={warera.permitWait && warera.permitWait.p95Millis >= SLOW_MILLIS ? 'warning' : undefined}
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
                <Count value={procedure.failures} tone={nonZero(procedure.failures, 'critical')} />
                <Count value={procedure.rateLimited} tone={nonZero(procedure.rateLimited)} />
                <Count value={procedure.retries} tone={nonZero(procedure.retries)} />
                <Count value={procedure.throttled} tone={nonZero(procedure.throttled)} />
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
