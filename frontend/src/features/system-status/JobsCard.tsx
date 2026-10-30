import { CalendarClockIcon, CircleCheckIcon, CircleXIcon, LoaderIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatTime } from '@/features/system-log/labels'
import { cn } from '@/lib/utils'
import type { SystemStatus } from './api'
import { JOBS, formatAgo, formatCount, formatDuration, scheduledTaskName, triggerName } from './labels'
import { Section, Empty } from './StatusParts'
import { TONE_TEXT } from './status'

export function JobsCard({ data, now }: { data: SystemStatus; now: number }) {
  return (
    <Section title="Hintergrund-Jobs" icon={CalendarClockIcon} description="Was BITS von sich aus tut, seit dem Start des Backends.">
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
                  {job.runningSince !== null ? (
                    <Badge>
                      <LoaderIcon aria-hidden className="motion-safe:animate-spin" />
                      Läuft
                    </Badge>
                  ) : job.lastStartedAt === null ? (
                    '–'
                  ) : job.lastFailure === null ? (
                    <Badge variant="success">
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
                  {formatCount(job.runs)} /{' '}
                  <span className={cn(job.failures > 0 && cn('font-medium', TONE_TEXT.critical))}>{formatCount(job.failures)}</span>
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
                {task.lastOutcome === 'ERROR' && (
                  <span className="inline-flex items-center gap-1 font-medium text-destructive">
                    {' '}
                    <CircleXIcon aria-hidden className="size-4" />
                    fehlgeschlagen
                  </span>
                )}
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
