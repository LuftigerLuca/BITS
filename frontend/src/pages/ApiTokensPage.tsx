import { PageHeader } from '@/components/PageHeader'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { ApiTokensCard } from '@/features/api-tokens/ApiTokensCard'
import { LOAD_ERROR } from '@/features/api-tokens/labels'
import { NewApiTokenCard } from '@/features/api-tokens/NewApiTokenCard'
import { useApiTokens } from '@/features/api-tokens/queries'

export function ApiTokensPage() {
  const tokens = useApiTokens()
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="API-Tokens" />
      {tokens.isPending ? (
        <LoadingState label="API-Tokens werden geladen …" />
      ) : tokens.isError ? (
        <ErrorState message={LOAD_ERROR} onRetry={() => tokens.refetch()} />
      ) : (
        <>
          <ApiTokensCard data={tokens.data} />
          <NewApiTokenCard scopes={tokens.data.scopes} />
        </>
      )}
    </div>
  )
}
