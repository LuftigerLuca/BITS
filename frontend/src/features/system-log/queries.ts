import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getSystemLog, getSystemLogItems, getSystemLogRoles, type SystemLogFilter } from '@/lib/api'

const SYSTEM_LOG = ['system-log'] as const

/**
 * Pages of the system log for [filter], loaded one after the other. BITS adds entries all the time, so the log is
 * loaded anew every time the page is opened instead of after the usual 30 seconds, and when the window gets focus
 * again, as it reads the database only.
 */
export function useSystemLog(filter: SystemLogFilter) {
  return useInfiniteQuery({
    queryKey: [...SYSTEM_LOG, 'entries', filter],
    queryFn: ({ pageParam }) => getSystemLog(filter, pageParam),
    initialPageParam: null as number | null,
    getNextPageParam: (page) => page.nextBefore,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
}

/** Loaded once the entry is expanded. */
export function useSystemLogItems(entryId: number, filter: Pick<SystemLogFilter, 'member' | 'roleId'>) {
  return useQuery({
    queryKey: [...SYSTEM_LOG, 'items', entryId, filter],
    queryFn: () => getSystemLogItems(entryId, filter),
  })
}

export function useSystemLogRoles() {
  return useQuery({ queryKey: [...SYSTEM_LOG, 'roles'], queryFn: getSystemLogRoles, staleTime: 0 })
}
