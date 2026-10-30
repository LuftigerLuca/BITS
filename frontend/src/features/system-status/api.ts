import { request } from '@/lib/api'

const SYSTEM_STATUS = '/web/admin/system-status'

export type StatusComponent = 'POSTGRES' | 'REDIS' | 'WARERA' | 'BOT'

/**
 * `up` is `null` while nothing is known yet. `checkedAt` is the last check, WarEra call or heartbeat of the bot,
 * `since` when `up` last changed. Times are epoch milliseconds.
 */
export interface ComponentStatus {
  component: StatusComponent
  up: boolean | null
  since: number | null
  checkedAt: number | null
  durationMillis: number | null
  lastFailureAt: number | null
  lastProblem: string | null
}

export interface ProcessInfo {
  /** The commit it was built from, `dev` outside of a deployment. */
  version: string
  startedAt: number
  heapUsedBytes: number
  heapMaxBytes: number
  threads: number
}

export interface LatencyStats {
  p50Millis: number
  p95Millis: number
  maxMillis: number
}

export type WareraCaller =
  | 'VERIFICATION'
  | 'ROLE_SYNC'
  | 'EMBASSY_SYNC'
  | 'PROFILE'
  | 'ROLE_EXPLANATION'
  | 'VOTE'
  | 'PING'
  | 'DASHBOARD'
  | 'BOT'
  | 'OTHER'

export interface ProcedureStats {
  procedure: string
  /** Requests, retries included. */
  total: number
  lastHour: number
  failures: number
  rateLimited: number
  retries: number
  throttled: number
  cacheHits: number
  cacheMisses: number
  latency: LatencyStats | null
}

export interface WareraDetails {
  limitPerMinute: number
  lastMinute: number
  peakPerMinute: number
  lastHour: number
  /** Buckets of 15 minutes over the last 24 hours, oldest first. */
  history: number[]
  procedures: ProcedureStats[]
  callers: { caller: WareraCaller; total: number; lastHour: number }[]
  permitWait: LatencyStats | null
}

export interface EndpointStats {
  /** Method and path pattern; `*` for requests that reached no endpoint, e.g. without the token. */
  endpoint: string
  total: number
  lastHour: number
  clientErrors: number
  serverErrors: number
  latency: LatencyStats | null
}

export interface BotCallStats {
  endpoint: string
  succeeded: number
  failed: number
  timedOut: number
  unreachable: number
}

export interface BotDetails {
  process: ProcessInfo
  gatewayStatus: string
  /** `-1` until the first heartbeat of the gateway. */
  gatewayPingMillis: number
  guildMembers: number | null
  /** Since the bot started. */
  backendCalls: BotCallStats[]
  discord: { total: number; rateLimited: number; refused: number; serverErrors: number }
  failureHistory: number[]
  discordHistory: number[]
}

/** The parts that could not be read, e.g. while Redis is down, are `null`. */
export interface EventDetails {
  history: number[]
  published: { type: string; total: number; lastHour: number }[]
  outboxPending: number | null
  outboxOldestAt: number | null
  streamLength: number | null
  deadLetters: number | null
  groups: { name: string; consumers: number; pending: number; lag: number | null }[] | null
}

export type BackgroundJob = 'ROLE_SYNC_REFRESH' | 'ROLE_SYNC' | 'EMBASSY_SYNC' | 'VOTE_EVALUATION' | 'VERIFICATION_CHECK'

export interface JobStatus {
  job: BackgroundJob
  runningSince: number | null
  /** A `SyncTrigger`, if the job has one. */
  runningTrigger: string | null
  lastStartedAt: number | null
  lastDurationMillis: number | null
  lastTrigger: string | null
  /** `null` if the last run went through. */
  lastFailure: string | null
  runs: number
  failures: number
}

export interface ScheduledTaskStatus {
  /** Class and method, e.g. `RoleSyncTrigger.hourly`. */
  name: string
  nextAt: number | null
  lastAt: number | null
  lastOutcome: 'STARTED' | 'SUCCESS' | 'ERROR' | null
}

/** Counts are since the backend started (`backend.startedAt`), those of the bot since the bot started. */
export interface SystemStatus {
  generatedAt: number
  backend: ProcessInfo
  components: ComponentStatus[]
  postgres: {
    sizeBytes: number | null
    schemaVersion: string | null
    poolActive: number | null
    poolIdle: number | null
    poolWaiting: number | null
    poolMax: number | null
  } | null
  redis: { usedMemoryBytes: number | null; maxMemoryBytes: number | null } | null
  bot: BotDetails | null
  warera: WareraDetails
  api: { history: number[]; endpoints: EndpointStats[] }
  events: EventDetails
  jobs: JobStatus[]
  scheduledTasks: ScheduledTaskStatus[]
}

export async function getSystemStatus(): Promise<SystemStatus> {
  return (await request(SYSTEM_STATUS)).json()
}
