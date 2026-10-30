import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getAuditActors, getAuditLog, type AuditLogFilter } from './api'

const AUDIT_LOG = ['audit-log'] as const

/**
 * Pages of the audit log for [filter], loaded one after the other. Changes are made on other pages, so the log is
 * loaded anew every time the page is opened instead of after the usual 30 seconds, and when the window gets focus
 * again, as it reads the database only.
 */
export function useAuditLog(filter: AuditLogFilter) {
  return useInfiniteQuery({
    queryKey: [...AUDIT_LOG, 'entries', filter],
    queryFn: ({ pageParam }) => getAuditLog(filter, pageParam),
    initialPageParam: null as number | null,
    getNextPageParam: (page) => page.nextBefore,
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
}

export function useAuditActors() {
  return useQuery({ queryKey: [...AUDIT_LOG, 'actors'], queryFn: getAuditActors, staleTime: 0 })
}
