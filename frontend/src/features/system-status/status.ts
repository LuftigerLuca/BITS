import { CircleCheckIcon, CircleXIcon, TriangleAlertIcon } from 'lucide-react'
import type { LatencyStats } from './api'
import { formatDuration } from './labels'

/**
 * How a value stands: fine, worth a look, or broken. The colour always comes with an icon and, for screen readers, a
 * word, so it never carries the meaning alone.
 */
export type Tone = 'good' | 'warning' | 'critical'

export const TONE_TEXT: Record<Tone, string> = { good: 'text-success', warning: 'text-warning', critical: 'text-destructive' }

export const TONE_BAR: Record<Tone, string> = { good: 'bg-success', warning: 'bg-warning', critical: 'bg-destructive' }

export const TONE_ICON = { good: CircleCheckIcon, warning: TriangleAlertIcon, critical: CircleXIcon }

export const TONE_LABEL: Record<Tone, string> = { good: 'in Ordnung', warning: 'auffällig', critical: 'gestört' }

/** [warning] when [value] is above zero; counts that should stay at zero. */
export function nonZero(value: number | null, tone: Tone = 'warning'): Tone | undefined {
  return value !== null && value > 0 ? tone : undefined
}

/** [warning] from [warnAt] of a limit on. */
export function share(used: number | null, limit: number | null, warnAt: number): Tone | undefined {
  return used !== null && limit !== null && limit > 0 && used / limit >= warnAt ? 'warning' : undefined
}

export const BUCKET_MILLIS = 15 * 60_000

/** From this share of the WarEra limit on, requests start to wait for each other. */
export const LIMIT_WARNING = 0.8

export const HEAP_WARNING = 0.85

export const SLOW_MILLIS = 1_000

/** The outbox resubmits after a minute; an event older than that did not reach Redis on the first try. */
export const OUTBOX_STUCK_MILLIS = 60_000

export const TIME_OF_DAY = new Intl.DateTimeFormat('de-DE', { timeStyle: 'short' })

export function latency(stats: LatencyStats | null): string {
  return stats ? `${formatDuration(stats.p50Millis)} / ${formatDuration(stats.p95Millis)}` : '–'
}
