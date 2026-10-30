import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import { StartVoteForm } from '@/features/votes/StartVoteForm'
import { OpenVotesCard, VoteArchiveCard } from '@/features/votes/VotesCards'
import { PageHeader } from '@/components/PageHeader'

export function VotesPage() {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Abstimmungen" />
      <FeatureOffNotice feature="VOTES">
        Neue Abstimmungen lassen sich erst wieder starten, wenn ein Admin sie unter „Verwaltung“ einschaltet. Laufende
        enden wie geplant.
      </FeatureOffNotice>
      <StartVoteForm />
      <OpenVotesCard />
      <VoteArchiveCard />
    </div>
  )
}
