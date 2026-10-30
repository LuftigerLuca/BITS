import { ErrorState, LoadingState } from '@/components/QueryState'
import { EmbassySettingsCard } from '@/features/embassies/EmbassySettingsCard'
import { EmbassyThreadsCard } from '@/features/embassies/EmbassyThreadsCard'
import { FeatureOffNotice } from '@/features/feature-toggles/FeatureOffNotice'
import { ERROR_MESSAGE } from '@/features/embassies/labels'
import { useEmbassies } from '@/features/embassies/queries'
import { PageHeader } from '@/components/PageHeader'

export function EmbassiesPage() {
  const embassies = useEmbassies()
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Botschaften" />
      <FeatureOffNotice feature="EMBASSIES">
        Die Botschaften pausieren: Du kannst die Einstellungen weiter ändern, aber BITS gleicht die Threads erst wieder
        ab, wenn sie unter „Verwaltung“ eingeschaltet sind.
      </FeatureOffNotice>
      {embassies.isPending ? (
        <LoadingState label="Botschaften werden geladen …" />
      ) : embassies.isError ? (
        <ErrorState message={ERROR_MESSAGE} onRetry={() => embassies.refetch()} />
      ) : (
        <>
          <EmbassyThreadsCard embassies={embassies.data} />
          <EmbassySettingsCard embassies={embassies.data} />
        </>
      )}
    </div>
  )
}
