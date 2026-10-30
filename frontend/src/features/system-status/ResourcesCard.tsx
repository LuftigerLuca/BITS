import { ServerIcon, TriangleAlertIcon } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatTime } from '@/features/system-log/labels'
import { cn } from '@/lib/utils'
import type { SystemStatus } from './api'
import { formatAgo, formatBytes, formatCount } from './labels'
import { ToneIcon, Fact, Section } from './StatusParts'
import { TONE_TEXT, TONE_BAR, share, HEAP_WARNING } from './status'

export function ResourcesCard({ data, now }: { data: SystemStatus; now: number }) {
  const { backend, bot, postgres, redis } = data
  const processes = [{ name: 'Backend', process: backend }, ...(bot ? [{ name: 'Bot', process: bot.process }] : [])]
  const mismatch = bot && bot.process.version !== backend.version
  return (
    <Section title="Versionen und Ressourcen" icon={ServerIcon} description="Was gerade läuft und wie viel Speicher es braucht.">
      {mismatch && (
        <Alert variant="warning" role="status">
          <TriangleAlertIcon aria-hidden />
          <AlertDescription>
            Backend und Bot laufen mit verschiedenen Versionen; nach einem Deploy sollte das nur kurz so sein.
          </AlertDescription>
        </Alert>
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
              <HeapCell used={process.heapUsedBytes} max={process.heapMaxBytes} />
              <TableCell className="text-right tabular-nums">{formatCount(process.threads)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <Fact label="Datenbank">{postgres ? formatBytes(postgres.sizeBytes) : 'unbekannt'}</Fact>
        <Fact label="Schema-Version">{postgres?.schemaVersion ?? 'unbekannt'}</Fact>
        <Fact label="Verbindungen">
          {postgres?.poolMax == null ? (
            'unbekannt'
          ) : (
            <span className={cn('inline-flex items-center gap-1', (postgres.poolWaiting ?? 0) > 0 && TONE_TEXT.warning)}>
              {(postgres.poolWaiting ?? 0) > 0 && <ToneIcon tone="warning" />}
              {`${postgres.poolActive ?? 0} aktiv, ${postgres.poolIdle ?? 0} frei, ${postgres.poolWaiting ?? 0} wartend, höchstens ${postgres.poolMax}`}
            </span>
          )}
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

/** Heap in use, with a bar against the maximum that turns to warning when the process runs short. */
function HeapCell({ used, max }: { used: number; max: number }) {
  const tone = share(used, max, HEAP_WARNING)
  return (
    <TableCell className="text-right tabular-nums">
      <span className="flex flex-col items-end gap-1">
        <span className={cn('inline-flex items-center gap-1', tone && TONE_TEXT[tone])}>
          {tone && <ToneIcon tone={tone} />}
          {formatBytes(used)} von {formatBytes(max)}
        </span>
        {max > 0 && (
          <span className="block h-1.5 w-full overflow-hidden rounded-full bg-muted" aria-hidden>
            <span
              className={cn('block h-full rounded-full', tone ? TONE_BAR[tone] : 'bg-primary')}
              style={{ width: `${Math.min(100, (used / max) * 100)}%` }}
            />
          </span>
        )}
      </span>
    </TableCell>
  )
}
