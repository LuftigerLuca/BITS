import { useState } from 'react'
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { type ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart'
import type { OverviewHistory, OverviewHistoryDay } from '@/lib/api'
import { formatCount } from './labels'
import { useOverviewHistory } from './queries'

const METRICS = {
  members: 'Mitglieder',
  verifiedMembers: 'Verifiziert',
  citizens: 'Bürger',
} as const satisfies Partial<Record<keyof OverviewHistoryDay, string>>

type Metric = keyof typeof METRICS

const RANGES = [
  { days: 30, label: '30 Tage' },
  { days: 365, label: '1 Jahr' },
] as const

const DAY = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })
const MONTH = new Intl.DateTimeFormat('de-DE', { month: 'short', year: '2-digit', timeZone: 'UTC' })
const FULL_DAY = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeZone: 'UTC' })

/** One point per day of the range; a day without a snapshot is a gap like a figure that could not be read. */
function chartPoints(history: OverviewHistory, metric: Metric): { day: string; value: number | null }[] {
  const byDay = new Map(history.days.map((day) => [day.day, day[metric]]))
  const points = []
  for (let day = new Date(`${history.from}T00:00:00Z`); day <= new Date(`${history.to}T00:00:00Z`); ) {
    const iso = day.toISOString().slice(0, 10)
    points.push({ day: iso, value: byDay.get(iso) ?? null })
    day = new Date(day.getTime() + 24 * 60 * 60 * 1000)
  }
  return points
}

/** How the chosen figure changed over the range, for everybody who cannot see the line. */
function summary(points: { day: string; value: number | null }[]): string {
  const known = points.filter((point): point is { day: string; value: number } => point.value !== null)
  if (known.length === 0) return 'Für diesen Zeitraum gibt es noch keine Werte.'
  const first = known[0]
  const last = known[known.length - 1]
  const change = last.value - first.value
  const sign = change > 0 ? '+' : change < 0 ? '−' : '±'
  return `${formatCount(last.value)} am ${formatDay(last.day)}, ${sign}${formatCount(Math.abs(change))} seit ${formatDay(first.day)}`
}

function formatDay(iso: string): string {
  return FULL_DAY.format(new Date(`${iso}T00:00:00Z`))
}

/** The key figures day by day, one at a time, since each has its own scale. */
export function OverviewHistoryCard() {
  const [metric, setMetric] = useState<Metric>('members')
  const [days, setDays] = useState<number>(RANGES[0].days)
  const history = useOverviewHistory(days)

  return (
    <Card>
      <CardHeader className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <CardTitle>Verlauf</CardTitle>
          <CardDescription>
            {history.data ? summary(chartPoints(history.data, metric)) : 'Kennzahlen Tag für Tag'}
          </CardDescription>
        </div>
        <div className="flex flex-wrap gap-2">
          <Toggle label="Kennzahl" options={Object.entries(METRICS)} value={metric} onChange={(key) => setMetric(key as Metric)} />
          <Toggle
            label="Zeitraum"
            options={RANGES.map((range) => [String(range.days), range.label])}
            value={String(days)}
            onChange={(key) => setDays(Number(key))}
          />
        </div>
      </CardHeader>
      <CardContent>
        {history.isPending ? (
          <LoadingState label="Verlauf wird geladen …" />
        ) : history.isError ? (
          <ErrorState message="Der Verlauf konnte nicht geladen werden." onRetry={() => history.refetch()} />
        ) : (
          <HistoryChart points={chartPoints(history.data, metric)} label={METRICS[metric]} yearly={days > 31} />
        )}
      </CardContent>
    </Card>
  )
}

function HistoryChart({
  points,
  label,
  yearly,
}: {
  points: { day: string; value: number | null }[]
  label: string
  yearly: boolean
}) {
  const config = { value: { label, color: 'var(--primary)' } } satisfies ChartConfig
  const tick = (iso: string) => (yearly ? MONTH : DAY).format(new Date(`${iso}T00:00:00Z`))
  return (
    <ChartContainer config={config} className="aspect-auto h-64 w-full" aria-hidden="true">
      <LineChart data={points} margin={{ left: 4, right: 12 }}>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="day" tickLine={false} axisLine={false} tickMargin={8} minTickGap={32} tickFormatter={tick} />
        <YAxis
          width={48}
          tickLine={false}
          axisLine={false}
          allowDecimals={false}
          domain={['auto', 'auto']}
          tickFormatter={(value: number) => formatCount(value)}
        />
        <ChartTooltip
          content={<ChartTooltipContent labelFormatter={(_, payload) => formatDay(String(payload[0]?.payload.day))} />}
        />
        {/* No line through a gap, so a missing day does not look like a known one. */}
        <Line dataKey="value" type="monotone" stroke="var(--color-value)" strokeWidth={2} dot={false} connectNulls={false} />
      </LineChart>
    </ChartContainer>
  )
}

function Toggle({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: [string, string][]
  value: string
  onChange: (key: string) => void
}) {
  return (
    <div role="group" aria-label={label} className="flex rounded-md border p-0.5">
      {options.map(([key, text]) => (
        <Button
          key={key}
          type="button"
          size="xs"
          variant={value === key ? 'secondary' : 'ghost'}
          aria-pressed={value === key}
          onClick={() => onChange(key)}
        >
          {text}
        </Button>
      ))}
    </div>
  )
}
