import { Navigate, Outlet } from 'react-router'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { useVoteAccess } from './queries'

/** The votes page is hidden from users who may not manage votes; the backend rejects their requests anyway. */
export function RequireVoteAccess() {
  const access = useVoteAccess()
  if (access.isPending) return <LoadingState />
  if (access.isError) {
    return (
      <ErrorState message="Ob du Abstimmungen verwalten darfst, konnte nicht geprüft werden." onRetry={() => access.refetch()} />
    )
  }
  if (!access.data.canManage) return <Navigate to="/" replace />
  return <Outlet />
}
