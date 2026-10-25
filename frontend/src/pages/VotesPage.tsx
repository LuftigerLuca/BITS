import { ManagerRolesCard } from '@/features/votes/ManagerRolesCard'
import { StartVoteForm } from '@/features/votes/StartVoteForm'
import { OpenVotesCard, VoteArchiveCard } from '@/features/votes/VotesCards'
import { useVoteAccess } from '@/features/votes/queries'

export function VotesPage() {
  const access = useVoteAccess()
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Abstimmungen</h1>
      <StartVoteForm />
      <OpenVotesCard />
      <VoteArchiveCard />
      {access.data?.canConfigure && <ManagerRolesCard />}
    </div>
  )
}
