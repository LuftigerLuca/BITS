import { BotPresenceCard } from '@/features/bot-presence/BotPresenceCard'
import { FeaturesCard } from '@/features/feature-toggles/FeaturesCard'
import { HoneypotCard } from '@/features/honeypot/HoneypotCard'
import { CountrySettingCard } from '@/features/overview/CountrySettingCard'
import { ManagerRolesCard } from '@/features/votes/ManagerRolesCard'
import { PartyColorsCard } from '@/features/votes/PartyColorsCard'
import { ResultImageCard } from '@/features/votes/ResultImageCard'
import { PageHeader } from '@/components/PageHeader'

export function AdminPage() {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Verwaltung" />
      <FeaturesCard />
      <CountrySettingCard />
      <BotPresenceCard />
      <HoneypotCard />
      <ManagerRolesCard />
      <ResultImageCard />
      <PartyColorsCard />
    </div>
  )
}
