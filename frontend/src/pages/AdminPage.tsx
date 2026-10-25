import { CountrySettingCard } from '@/features/overview/CountrySettingCard'
import { ManagerRolesCard } from '@/features/votes/ManagerRolesCard'

export function AdminPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Verwaltung</h1>
      <CountrySettingCard />
      <ManagerRolesCard />
    </div>
  )
}
