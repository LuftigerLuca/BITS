import { ErrorState, LoadingState } from '@/components/QueryState'
import { EmbassySettingsCard } from '@/features/embassies/EmbassySettingsCard'
import { EmbassyThreadsCard } from '@/features/embassies/EmbassyThreadsCard'
import { ERROR_MESSAGE } from '@/features/embassies/labels'
import { useEmbassies } from '@/features/embassies/queries'

export function EmbassiesPage() {
  const embassies = useEmbassies()
  return (
    <div className="flex flex-col gap-4">
      <h1 className="font-heading text-xl font-semibold">Botschaften</h1>
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
