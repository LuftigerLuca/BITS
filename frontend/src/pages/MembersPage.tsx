import { useSearchParams } from 'react-router'
import { MemberDetail } from '@/features/members/MemberDetail'
import { MemberSearchCard } from '@/features/members/MemberSearchCard'
import { PageHeader } from '@/components/PageHeader'

/** The member shown is in the URL as `?id=<Discord ID>`, so it can be linked and survives a reload. */
export function MembersPage() {
  const [searchParams] = useSearchParams()
  const discordUserId = searchParams.get('id')

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Mitglieder" />
      <MemberSearchCard />
      {discordUserId && <MemberDetail key={discordUserId} discordUserId={discordUserId} />}
    </div>
  )
}
