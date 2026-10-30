import { TRIGGERS } from '@/features/system-log/labels'
import type { SyncTrigger } from '@/features/system-log/api'
import type { BackgroundJob, StatusComponent, WareraCaller } from './api'

export const COMPONENTS: Record<StatusComponent, string> = {
  POSTGRES: 'Datenbank (Postgres)',
  REDIS: 'Redis',
  WARERA: 'WarEra-API',
  BOT: 'Discord-Bot',
}

export const CALLERS: Record<WareraCaller, string> = {
  VERIFICATION: 'Verifizierung',
  ROLE_SYNC: 'Rollen-Sync',
  EMBASSY_SYNC: 'Botschafts-Sync',
  PROFILE: '/profil',
  ROLE_EXPLANATION: '/rollen',
  VOTE: 'Abstimmungen',
  PING: '/ping',
  DASHBOARD: 'Dashboard',
  BOT: 'Sonstige Bot-Anfragen',
  OTHER: 'Sonstiges',
}

export const JOBS: Record<BackgroundJob, string> = {
  ROLE_SYNC_REFRESH: 'WarEra-Daten für den Rollen-Sync laden',
  ROLE_SYNC: 'Rollen-Sync',
  EMBASSY_SYNC: 'Botschafts-Sync',
  VOTE_EVALUATION: 'Abstimmung auswerten',
  VERIFICATION_CHECK: 'Verifizierung prüfen',
}

/** Jobs on a schedule by class and method; unknown ones keep their technical name. */
const SCHEDULED_TASKS: Record<string, string> = {
  'RoleSyncTrigger.hourly': 'Stündlicher Rollen-Sync',
  'EmbassySyncTrigger.hourly': 'Stündlicher Botschafts-Sync',
  'EmbassyThreadCreationService.expire': 'Unbeantwortete Thread-Erstellungen beenden',
  'OutboxMonitor.check': 'Wartende Events prüfen',
  'OutboxResubmitter.resubmit': 'Wartende Events erneut senden',
  'SystemLogCleanup.deleteOld': 'System-Log aufräumen',
  'VerificationCleanup.deleteExpired': 'Abgelaufene Verifizierungen löschen',
  'PingLogCleanup.deleteOld': 'Alte Pings löschen',
  'VoteJobs.run': 'Abstimmungen beenden und auswerten',
  'OverviewSnapshotJob.record': 'Kennzahlen für den Verlauf festhalten',
  'Moments.everyHour': 'Zeitereignis (stündlich)',
  'Moments.everyMidnight': 'Zeitereignis (Mitternacht)',
}

export function scheduledTaskName(name: string): string {
  return SCHEDULED_TASKS[name] ?? name
}

export function triggerName(trigger: string | null): string | null {
  if (trigger === null) return null
  return TRIGGERS[trigger as SyncTrigger] ?? trigger
}

/** `*` stands for requests that reached no endpoint. */
export function endpointName(endpoint: string): string {
  return endpoint.endsWith(' *') ? `${endpoint.slice(0, -2)} (abgewiesen, z. B. ohne Token)` : endpoint
}

const COUNT = new Intl.NumberFormat('de-DE')

export function formatCount(value: number): string {
  return COUNT.format(value)
}

export function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes < 0) return 'unbekannt'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: unit === 0 ? 0 : 1 }).format(value)} ${units[unit]}`
}

export function formatDuration(millis: number): string {
  if (millis < 1_000) return `${millis} ms`
  const seconds = Math.round(millis / 1_000)
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ${seconds % 60} s`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ${minutes % 60} min`
  return `${Math.floor(hours / 24)} d ${hours % 24} h`
}

/** How long ago [time] was, seen from [now]; both epoch milliseconds. */
export function formatAgo(time: number, now: number): string {
  const millis = Math.max(0, now - time)
  if (millis < 5_000) return 'gerade eben'
  return `vor ${formatDuration(Math.round(millis / 1_000) * 1_000)}`
}

export const ERROR_MESSAGE = 'Der System-Status konnte nicht geladen werden. Bitte versuche es später erneut.'
