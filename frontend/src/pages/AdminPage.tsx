import { BotPresenceCard } from '@/features/bot-presence/BotPresenceCard'
import { FeaturesCard } from '@/features/feature-toggles/FeaturesCard'
import { CountrySettingCard } from '@/features/overview/CountrySettingCard'
import { ManagerRolesCard } from '@/features/votes/ManagerRolesCard'
import { PartyColorsCard } from '@/features/votes/PartyColorsCard'
import { ResultImageCard } from '@/features/votes/ResultImageCard'

export function AdminPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Verwaltung</h1>
      <FeaturesCard />
      <CountrySettingCard />
      <BotPresenceCard />
      <ManagerRolesCard />
      <ResultImageCard />
      <PartyColorsCard />
    </div>
  )
}
