import {
  BotIcon,
  CircleCheckIcon,
  CircleHelpIcon,
  CircleXIcon,
  ClockIcon,
  DatabaseIcon,
  GlobeIcon,
  type LucideIcon,
  TimerIcon,
  TriangleAlertIcon,
  ZapIcon,
} from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatTime } from '@/features/system-log/labels'
import { cn } from '@/lib/utils'
import type { ComponentStatus, StatusComponent } from './api'
import { COMPONENTS, formatAgo, formatDuration } from './labels'
import { Fact } from './StatusParts'

const COMPONENT_ICONS: Record<StatusComponent, LucideIcon> = {
  POSTGRES: DatabaseIcon,
  REDIS: ZapIcon,
  WARERA: GlobeIcon,
  BOT: BotIcon,
}

/** One line on top that says whether everything is reachable, so the page answers that before any detail. */
export function Summary({ components }: { components: ComponentStatus[] }) {
  const down = components.filter((component) => component.up === false)
  const unknown = components.filter((component) => component.up === null)
  if (down.length > 0) {
    return (
      <Alert variant="destructive" role="status" className="border-destructive/40 bg-destructive/10">
        <CircleXIcon aria-hidden />
        <AlertTitle>Gestört: {down.map((component) => COMPONENTS[component.component]).join(', ')}</AlertTitle>
        <AlertDescription>Die Karten darunter zeigen, seit wann und was zuletzt schiefging.</AlertDescription>
      </Alert>
    )
  }
  if (unknown.length > 0) {
    return (
      <Alert variant="warning" role="status">
        <CircleHelpIcon aria-hidden />
        <AlertTitle>Noch ohne Rückmeldung: {unknown.map((component) => COMPONENTS[component.component]).join(', ')}</AlertTitle>
        <AlertDescription>Alle anderen Komponenten sind erreichbar.</AlertDescription>
      </Alert>
    )
  }
  return (
    <Alert variant="success" role="status">
      <CircleCheckIcon aria-hidden />
      <AlertTitle>Alle Komponenten erreichbar</AlertTitle>
    </Alert>
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
    <Badge variant="success">
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

export function ComponentCard({ status, now }: { status: ComponentStatus; now: number }) {
  const name = COMPONENTS[status.component]
  const contact = status.component === 'BOT' ? 'Letzter Heartbeat' : status.component === 'WARERA' ? 'Letzter Kontakt' : 'Geprüft'
  const duration = status.component === 'BOT' ? 'Gateway-Ping' : 'Antwortzeit'
  const Icon = COMPONENT_ICONS[status.component]
  return (
    <Card
      size="sm"
      role="group"
      aria-label={name}
      className={cn(
        'border-l-4',
        status.up === true && 'border-l-success',
        status.up === false && 'border-l-destructive bg-destructive/5',
        status.up === null && 'border-l-border',
      )}
    >
      <CardHeader>
        {/* The badge stays out of the heading, so the heading is only the name. */}
        <div className="flex items-center justify-between gap-2">
          <span className="flex items-center gap-2">
            <Icon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
            <CardTitle>{name}</CardTitle>
          </span>
          <UpBadge up={status.up} />
        </div>
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
          {status.checkedAt !== null && (
            <Fact label={contact} icon={ClockIcon}>
              {formatAgo(status.checkedAt, now)}
            </Fact>
          )}
          {status.durationMillis !== null && (
            <Fact label={duration} icon={TimerIcon}>
              {formatDuration(status.durationMillis)}
            </Fact>
          )}
          {status.lastProblem !== null && (
            <Fact label="Letzte Störung" icon={TriangleAlertIcon}>
              {status.lastFailureAt !== null && `${formatTime(status.lastFailureAt)}: `}
              {status.lastProblem}
            </Fact>
          )}
        </dl>
      </CardContent>
    </Card>
  )
}
