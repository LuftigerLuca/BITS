import { useSearchParams } from 'react-router'
import { MemberDetail } from '@/features/members/MemberDetail'
import { MemberSearchCard } from '@/features/members/MemberSearchCard'

/** The member shown is in the URL as `?id=<Discord ID>`, so it can be linked and survives a reload. */
export function MembersPage() {
  const [searchParams] = useSearchParams()
  const discordUserId = searchParams.get('id')

  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Mitglieder</h1>
      <MemberSearchCard />
      {discordUserId && <MemberDetail key={discordUserId} discordUserId={discordUserId} />}
    </div>
  )
}
