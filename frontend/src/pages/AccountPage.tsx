import { DataCard } from '@/features/account/DataCard'
import { LinkCard } from '@/features/account/LinkCard'
import { RolesCard } from '@/features/account/RolesCard'
import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'

export function AccountPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Mein Account</h1>
      <LinkCard />
      <FeatureOffNotice feature="ROLE_SYNC">
        Der Rollen-Sync pausiert: Deine Rollen auf dem Server bleiben, wie sie sind, bis ein Admin ihn wieder einschaltet.
      </FeatureOffNotice>
      <RolesCard />
      <DataCard />
    </div>
  )
}
