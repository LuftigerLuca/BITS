import { useSearchParams } from 'react-router'
import { BotPresenceCard } from '@/features/bot-presence/BotPresenceCard'
import { FeaturesCard } from '@/features/feature-toggles/FeaturesCard'
import { HoneypotCard } from '@/features/honeypot/HoneypotCard'
import { CountrySettingCard } from '@/features/overview/CountrySettingCard'
import { TopForumsCard } from '@/features/tops/TopForumsCard'
import { TopManagerRolesCard } from '@/features/tops/TopManagerRolesCard'
import { VerifyPanelCard } from '@/features/verify-panel/VerifyPanelCard'
import { ManagerRolesCard } from '@/features/votes/ManagerRolesCard'
import { PartyColorsCard } from '@/features/votes/PartyColorsCard'
import { ResultImageCard } from '@/features/votes/ResultImageCard'
import { PageHeader } from '@/components/PageHeader'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ADMIN_TABS, adminTabOf, type AdminTab } from '@/lib/adminTabs'

export function AdminPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = adminTabOf(searchParams.get('tab'))

  function onTabChange(value: AdminTab) {
    // Replace rather than push, so that "back" leaves the page instead of stepping through the tabs.
    setSearchParams(
      (params) => {
        params.set('tab', value)
        return params
      },
      { replace: true },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Verwaltung" />
      <Tabs value={tab} onValueChange={(value) => onTabChange(adminTabOf(String(value)))} className="gap-4">
        <TabsList aria-label="Bereiche der Verwaltung" activateOnFocus>
          {ADMIN_TABS.map(({ value, label }) => (
            <TabsTrigger key={value} value={value} className="px-3">
              {label}
            </TabsTrigger>
          ))}
        </TabsList>
        {/* Inactive tabs stay mounted, so that unsaved input survives switching back and forth. */}
        <TabsContent value="funktionen" keepMounted className="flex flex-col gap-4">
          <FeaturesCard />
        </TabsContent>
        <TabsContent value="server" keepMounted className="flex flex-col gap-4">
          <CountrySettingCard />
          <BotPresenceCard />
          <VerifyPanelCard />
          <HoneypotCard />
        </TabsContent>
        <TabsContent value="abstimmungen" keepMounted className="flex flex-col gap-4">
          <ManagerRolesCard />
          <ResultImageCard />
          <PartyColorsCard />
        </TabsContent>
        <TabsContent value="tops" keepMounted className="flex flex-col gap-4">
          <TopForumsCard />
          <TopManagerRolesCard />
        </TabsContent>
      </Tabs>
    </div>
  )
}
