import { DataCard } from '@/features/account/DataCard'
import { LinkCard } from '@/features/account/LinkCard'
import { RolesCard } from '@/features/account/RolesCard'
import { useFeatureEnabled } from '@/features/feature-toggles/queries'

export function AccountPage() {
  // Shown unless the role sync is known to be off, like the pages in the navigation.
  const roleSync = useFeatureEnabled('ROLE_SYNC')

  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Mein Account</h1>
      <LinkCard />
      {roleSync !== false && <RolesCard />}
      <DataCard />
    </div>
  )
}
