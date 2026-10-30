import { useQuery } from '@tanstack/react-query'
import { getSystemStatus } from './api'

/** Reloaded every 15 seconds while the page is open, as often as the backend checks Postgres and Redis. */
export function useSystemStatus() {
  return useQuery({ queryKey: ['system-status'], queryFn: getSystemStatus, refetchInterval: 15_000, staleTime: 0 })
}
