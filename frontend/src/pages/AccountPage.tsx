import { DataCard } from '@/features/account/DataCard'
import { LinkCard } from '@/features/account/LinkCard'
import { RolesCard } from '@/features/account/RolesCard'

export function AccountPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Mein Account</h1>
      <LinkCard />
      <RolesCard />
      <DataCard />
    </div>
  )
}
