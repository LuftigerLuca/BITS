import { useQuery } from '@tanstack/react-query'
import { getCountries } from '@/lib/api'

/**
 * The countries of WarEra hardly ever change, so they are loaded once per visit, and only when needed. Shared by the
 * role sync and the overview.
 */
export function useCountries(enabled: boolean) {
  return useQuery({ queryKey: ['countries'], queryFn: getCountries, enabled, staleTime: Infinity })
}
