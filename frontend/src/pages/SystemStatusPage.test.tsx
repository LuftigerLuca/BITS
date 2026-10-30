import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import type { Me, SystemStatus } from '@/lib/api'
import { json, stubFetch } from '@/test/fetch'
import { renderApp } from '@/test/renderApp'

const admin: Me = { name: 'Luca', avatarUrl: null, role: 'ADMIN', wareraUsername: null, discordUser: true }

const now = Date.UTC(2026, 0, 1, 12, 0)
const history = Array.from({ length: 96 }, (_, index) => (index === 95 ? 12 : 0))
const process = { version: 'abc1234', startedAt: now - 3_600_000, heapUsedBytes: 100 * 1024 * 1024, heapMaxBytes: 512 * 1024 * 1024, threads: 40 }

const status: SystemStatus = {
  generatedAt: now,
  backend: process,
  components: [
    { component: 'POSTGRES', up: true, since: now - 3_600_000, checkedAt: now - 3_000, durationMillis: 2, lastFailureAt: null, lastProblem: null },
    { component: 'REDIS', up: true, since: now - 3_600_000, checkedAt: now - 3_000, durationMillis: 1, lastFailureAt: null, lastProblem: null },
    {
      component: 'WARERA',
      up: false,
      since: now - 120_000,
      checkedAt: now - 60_000,
      durationMillis: 10_000,
      lastFailureAt: now - 60_000,
      lastProblem: 'Keine Antwort',
    },
    { component: 'BOT', up: true, since: now - 3_000_000, checkedAt: now - 10_000, durationMillis: 42, lastFailureAt: null, lastProblem: null },
  ],
  postgres: { sizeBytes: 20 * 1024 * 1024, schemaVersion: '28', poolActive: 1, poolIdle: 9, poolWaiting: 0, poolMax: 10 },
  redis: { usedMemoryBytes: 2 * 1024 * 1024, maxMemoryBytes: null },
  bot: {
    process: { ...process, version: 'def5678', startedAt: now - 3_000_000 },
    gatewayStatus: 'CONNECTED',
    gatewayPingMillis: 42,
    guildMembers: 250,
    backendCalls: [{ endpoint: 'POST /api/bot-heartbeat', succeeded: 100, failed: 0, timedOut: 1, unreachable: 2 }],
    discord: { total: 500, rateLimited: 3, refused: 1, serverErrors: 0 },
    failureHistory: history,
    discordHistory: history,
  },
  warera: {
    limitPerMinute: 90,
    lastMinute: 12,
    peakPerMinute: 85,
    lastHour: 300,
    history,
    procedures: [
      {
        procedure: 'user.getUserLite',
        total: 280,
        lastHour: 250,
        failures: 4,
        rateLimited: 2,
        retries: 3,
        throttled: 0,
        cacheHits: 0,
        cacheMisses: 0,
        latency: { p50Millis: 100, p95Millis: 250, maxMillis: 900 },
      },
      {
        procedure: 'country.getAllCountries',
        total: 2,
        lastHour: 1,
        failures: 0,
        rateLimited: 0,
        retries: 0,
        throttled: 0,
        cacheHits: 18,
        cacheMisses: 2,
        latency: null,
      },
    ],
    callers: [
      { caller: 'ROLE_SYNC', total: 270, lastHour: 240 },
      { caller: 'VERIFICATION', total: 12, lastHour: 11 },
    ],
    permitWait: { p50Millis: 5, p95Millis: 1_500, maxMillis: 2_000 },
  },
  api: {
    history,
    endpoints: [
      { endpoint: 'POST /api/bot-heartbeat', total: 120, lastHour: 120, clientErrors: 0, serverErrors: 0, latency: null },
      { endpoint: 'GET *', total: 3, lastHour: 3, clientErrors: 3, serverErrors: 0, latency: null },
    ],
  },
  events: {
    history,
    published: [{ type: 'RoleSyncEvent', total: 5, lastHour: 1 }],
    outboxPending: 0,
    outboxOldestAt: null,
    streamLength: 40,
    deadLetters: 1,
    groups: [{ name: 'bot', consumers: 1, pending: 0, lag: 2 }],
  },
  jobs: [
    {
      job: 'ROLE_SYNC',
      runningSince: null,
      runningTrigger: null,
      lastStartedAt: now - 600_000,
      lastDurationMillis: 65_000,
      lastTrigger: 'HOURLY',
      lastFailure: null,
      runs: 3,
      failures: 0,
    },
    {
      job: 'EMBASSY_SYNC',
      runningSince: null,
      runningTrigger: null,
      lastStartedAt: now - 600_000,
      lastDurationMillis: 1_000,
      lastTrigger: 'RULE_CHANGE',
      lastFailure: 'Discord nicht erreichbar',
      runs: 2,
      failures: 1,
    },
  ],
  scheduledTasks: [{ name: 'RoleSyncTrigger.hourly', nextAt: now + 3_600_000, lastAt: now - 600_000, lastOutcome: 'SUCCESS' }],
}

function stub(body: SystemStatus = status) {
  stubFetch({
    'GET /web/me': () => json(admin),
    'GET /web/admin/system-status': () => json(body),
  })
}

describe('system status page', () => {
  it('shows each component with whether it is up', async () => {
    stub()
    renderApp('/admin/system-status')

    const warera = await screen.findByRole('group', { name: 'WarEra-API' })
    expect(within(warera).getByText('Gestört')).toBeInTheDocument()
    expect(within(warera).getByText(/Keine Antwort/)).toBeInTheDocument()
    expect(within(warera).getByText('vor 1 min 0 s')).toBeInTheDocument()
    expect(within(screen.getByRole('group', { name: 'Datenbank (Postgres)' })).getByText('Erreichbar')).toBeInTheDocument()
    expect(within(screen.getByRole('group', { name: 'Discord-Bot' })).getByText('42 ms')).toBeInTheDocument()
  })

  it('shows the WarEra calls by procedure and by what they were made for', async () => {
    stub()
    renderApp('/admin/system-status')

    expect(await screen.findByText('12 / 90')).toBeInTheDocument()
    const procedures = screen.getByRole('table', { name: 'WarEra-Aufrufe nach Prozedur' })
    expect(within(procedures).getByText('user.getUserLite')).toBeInTheDocument()
    expect(within(procedures).getByText('18 von 20 (90 %)')).toBeInTheDocument()
    expect(within(procedures).getByText('100 ms / 250 ms')).toBeInTheDocument()
    const callers = screen.getByRole('table', { name: 'WarEra-Aufrufe nach Auslöser' })
    expect(within(callers).getByText('Rollen-Sync')).toBeInTheDocument()
    expect(within(callers).getByText('Verifizierung')).toBeInTheDocument()
    expect(screen.getByText('Requests an WarEra: 12 in 24 Stunden, höchstens 12 in 15 Minuten')).toBeInTheDocument()
  })

  it('shows the traffic between bot and backend, the jobs and the versions', async () => {
    stub()
    renderApp('/admin/system-status')

    expect(await screen.findByText('GET (abgewiesen, z. B. ohne Token)')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Consumer-Groups des Bots' })).toHaveTextContent('bot02')
    const jobs = screen.getByRole('table', { name: 'Hintergrund-Jobs' })
    expect(within(jobs).getByText('(stündlich)')).toBeInTheDocument()
    expect(within(jobs).getByText('Discord nicht erreichbar')).toBeInTheDocument()
    expect(screen.getByText('Stündlicher Rollen-Sync')).toBeInTheDocument()
    expect(screen.getByText(/verschiedenen Versionen/)).toBeInTheDocument()
    expect(screen.getByText('2 MB, ohne Limit')).toBeInTheDocument()
  })

  it('says so before the first heartbeat of the bot', async () => {
    stub({ ...status, bot: null, components: status.components.map((c) => (c.component === 'BOT' ? { ...c, up: null } : c)) })
    renderApp('/admin/system-status')

    expect(await screen.findByText('Seit dem Start des Backends kam noch kein Heartbeat vom Bot.')).toBeInTheDocument()
    expect(within(screen.getByRole('group', { name: 'Discord-Bot' })).getByText('Unbekannt')).toBeInTheDocument()
  })

  it('is linked from the sidebar', async () => {
    stubFetch({
      'GET /web/me': () => json(admin),
      'GET /web/admin/overview': () => json({ members: null, verifiedMembers: null, country: null }),
      'GET /web/admin/role-sync/countries': () => json([]),
      'GET /web/admin/system-status': () => json(status),
    })
    const router = renderApp('/admin')

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    await userEvent.click(within(navigation).getByRole('link', { name: 'System-Status' }))

    expect(await screen.findByRole('heading', { name: 'System-Status' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/system-status')
  })

  it('keeps members out', async () => {
    stubFetch({ 'GET /web/me': () => json({ ...admin, role: 'USER' }) })

    const router = renderApp('/admin/system-status')

    expect(await screen.findByText('Willkommen, Luca')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('has no accessibility violations axe can find without a browser', async () => {
    stub()
    renderApp('/admin/system-status')
    await screen.findByText('12 / 90')

    // jsdom does not lay out the page, so contrast is left to the browser.
    const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })

    expect(results.violations.map((violation) => violation.id)).toEqual([])
  })
})
