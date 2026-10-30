import { type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { TableCell, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import { formatCount } from './labels'
import { type Tone, TONE_TEXT, TONE_BAR, TONE_ICON, TONE_LABEL, BUCKET_MILLIS, TIME_OF_DAY } from './status'

export function ToneIcon({ tone }: { tone: Tone }) {
  const Icon = TONE_ICON[tone]
  return (
    <>
      <Icon aria-hidden className={cn('size-4 shrink-0', TONE_TEXT[tone])} />
      <span className="sr-only">({TONE_LABEL[tone]})</span>
    </>
  )
}

/** A number in a table, coloured with its icon when it is worth a look. */
export function Count({ value, tone }: { value: number; tone?: Tone }) {
  return (
    <TableCell className={cn('text-right tabular-nums', tone && cn('font-medium', TONE_TEXT[tone]))}>
      <span className="inline-flex items-center justify-end gap-1">
        {tone && <ToneIcon tone={tone} />}
        <span>{formatCount(value)}</span>
      </span>
    </TableCell>
  )
}

export function Fact({ label, icon: Icon, children }: { label: string; icon?: LucideIcon; children: ReactNode }) {
  return (
    <>
      <dt className="flex items-center gap-1.5 text-muted-foreground">
        {Icon && <Icon aria-hidden className="size-3.5 shrink-0" />}
        {label}
      </dt>
      <dd className="break-words">{children}</dd>
    </>
  )
}

/**
 * A key figure. With [tone] it shows its state in colour and icon; with [meter] (0 to 1) a bar shows how much of a
 * limit it uses.
 */
export function Stat({ label, value, note, tone, meter }: { label: string; value: string; note?: string; tone?: Tone; meter?: number }) {
  return (
    <div className="flex flex-col">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={cn('flex items-center gap-1.5', tone && TONE_TEXT[tone])}>
        {tone && <ToneIcon tone={tone} />}
        <span className="font-heading text-2xl font-semibold tabular-nums">{value}</span>
      </span>
      {meter !== undefined && (
        <span className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-muted" aria-hidden>
          <span
            className={cn('block h-full rounded-full', tone ? TONE_BAR[tone] : 'bg-primary')}
            style={{ width: `${Math.min(100, Math.max(0, meter * 100))}%` }}
          />
        </span>
      )}
      {note && <span className="text-xs text-muted-foreground">{note}</span>}
    </div>
  )
}

/**
 * Counts per 15 minutes over the last 24 hours as bars, the newest on the right; each bar names its time and count
 * on hover. The tables next to it carry the same data in numbers.
 */
export function History({ label, buckets, now, tone }: { label: string; buckets: number[]; now: number; tone?: Tone }) {
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
                className={cn('w-full rounded-t-[2px]', tone ? TONE_BAR[tone] : 'bg-primary')}
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

export function Section({
  title,
  icon: Icon,
  description,
  children,
}: {
  title: string
  icon: LucideIcon
  description: string
  children: ReactNode
}) {
  return (
    <Card role="region" aria-label={title}>
      <CardHeader>
        <span className="flex items-center gap-2">
          <Icon aria-hidden className="size-5 shrink-0 text-primary" />
          <CardTitle>{title}</CardTitle>
        </span>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">{children}</CardContent>
    </Card>
  )
}

export function Empty({ columns, children }: { columns: number; children: ReactNode }) {
  return (
    <TableRow>
      <TableCell colSpan={columns} className="text-muted-foreground">
        {children}
      </TableCell>
    </TableRow>
  )
}
