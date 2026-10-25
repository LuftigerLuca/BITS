import { StartVoteForm } from '@/features/votes/StartVoteForm'
import { OpenVotesCard, VoteArchiveCard } from '@/features/votes/VotesCards'

export function VotesPage() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Abstimmungen</h1>
      <StartVoteForm />
      <OpenVotesCard />
      <VoteArchiveCard />
    </div>
  )
}
