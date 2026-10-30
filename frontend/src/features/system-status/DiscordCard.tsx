import { MessagesSquareIcon } from 'lucide-react'
import type { SystemStatus } from './api'
import { formatAgo, formatCount, formatDuration } from './labels'
import { Stat, History, Section } from './StatusParts'
import { nonZero, SLOW_MILLIS } from './status'

export function DiscordCard({ data, now }: { data: SystemStatus; now: number }) {
  const bot = data.bot
  return (
    <Section title="Discord" icon={MessagesSquareIcon} description="Wie der Bot mit Discord verbunden ist, aus seinem letzten Heartbeat.">
      {!bot ? (
        <p className="text-sm text-muted-foreground">Seit dem Start des Backends kam noch kein Heartbeat vom Bot.</p>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Gateway" value={bot.gatewayStatus} tone={bot.gatewayStatus === 'CONNECTED' ? 'good' : 'critical'} />
            <Stat
              label="Gateway-Ping"
              value={bot.gatewayPingMillis < 0 ? 'unbekannt' : formatDuration(bot.gatewayPingMillis)}
              tone={bot.gatewayPingMillis >= SLOW_MILLIS ? 'warning' : undefined}
            />
            <Stat label="Mitglieder" value={bot.guildMembers === null ? 'unbekannt' : formatCount(bot.guildMembers)} />
            <Stat
              label="REST-Anfragen an Discord"
              value={formatCount(bot.discord.total)}
              note={`seit Start des Bots ${formatAgo(bot.process.startedAt, now)}`}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat
              label="Rate-Limit (429)"
              value={formatCount(bot.discord.rateLimited)}
              note="Discord ließ den Bot warten"
              tone={nonZero(bot.discord.rateLimited)}
            />
            <Stat
              label="Abgelehnt"
              value={formatCount(bot.discord.refused)}
              note="z. B. fehlende Rechte für eine Rolle"
              tone={nonZero(bot.discord.refused)}
            />
            <Stat label="Serverfehler" value={formatCount(bot.discord.serverErrors)} tone={nonZero(bot.discord.serverErrors)} />
          </div>
          <History label="REST-Anfragen und Fehler bei Discord" buckets={bot.discordHistory} now={data.generatedAt} />
        </>
      )}
    </Section>
  )
}
