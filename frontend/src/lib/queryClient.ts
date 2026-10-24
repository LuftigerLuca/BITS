import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api'

/**
 * Loads, caches and reloads the dashboard's data. A request the backend rejected (4xx) is not repeated; one without an
 * answer or with a server error is tried once more before the page shows the error.
 */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
        staleTime: 30_000,
      },
    },
  })
}
