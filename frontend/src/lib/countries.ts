import { useQuery } from '@tanstack/react-query'
import { request, type Option } from '@/lib/api'

const ROLE_SYNC = '/web/admin/role-sync'

export async function getCountries(): Promise<Option[]> {
  return (await request(`${ROLE_SYNC}/countries`)).json()
}

/**
 * The countries of WarEra hardly ever change, so they are loaded once per visit, and only when needed. Shared by the
 * role sync and the overview.
 */
export function useCountries(enabled: boolean) {
  return useQuery({ queryKey: ['countries'], queryFn: getCountries, enabled, staleTime: Infinity })
}
