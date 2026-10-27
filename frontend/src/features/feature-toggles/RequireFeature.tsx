import { Navigate, Outlet } from 'react-router'
import { LoadingState } from '@/components/QueryState'
import type { Feature } from '@/lib/api'
import { useFeatures } from './queries'

/**
 * The pages of a feature an admin switched off are hidden, like their entries in the navigation. If the features
 * cannot be loaded, the page shows anyway: hiding it is for clarity, the backend does not depend on it.
 */
export function RequireFeature({ feature }: { feature: Feature }) {
  const features = useFeatures()
  if (features.isPending) return <LoadingState />
  if (features.data?.find((state) => state.feature === feature)?.enabled === false) return <Navigate to="/" replace />
  return <Outlet />
}
