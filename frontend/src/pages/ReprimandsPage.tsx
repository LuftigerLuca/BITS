import { AdminTabLink } from '@/components/AdminTabLink'
import { PageHeader } from '@/components/PageHeader'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import { LOAD_ERROR } from '@/features/reprimands/labels'
import { useReprimands } from '@/features/reprimands/queries'
import { ReprimandCountsCard, ReprimandListCard } from '@/features/reprimands/ReprimandsCards'

export function ReprimandsPage() {
  const reprimands = useReprimands()
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Rügen" />
      <FeatureOffNotice feature="REPRIMANDS">
        /reprimand geht erst, wenn ein Admin Rügen unter <AdminTabLink tab="funktionen" /> einschaltet. Bestehende
        Rügen zählen weiter und lassen sich hier zurücknehmen.
      </FeatureOffNotice>
      {reprimands.isPending ? (
        <LoadingState label="Rügen werden geladen …" />
      ) : reprimands.isError ? (
        <ErrorState message={LOAD_ERROR} onRetry={() => reprimands.refetch()} />
      ) : (
        <>
          <ReprimandCountsCard counts={reprimands.data.counts} />
          <ReprimandListCard reprimands={reprimands.data.reprimands} />
        </>
      )}
    </div>
  )
}
