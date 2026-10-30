import { RefreshCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * Loads data anew on request. The dashboard does not reload when the window gets focus again, so data that BITS judges
 * with fresh WarEra data offers this instead.
 */
export function RefreshButton({ query }: { query: { isFetching: boolean; refetch: () => unknown } }) {
  return (
    <Button variant="outline" size="sm" disabled={query.isFetching} onClick={() => query.refetch()}>
      <RefreshCwIcon className={query.isFetching ? 'animate-spin' : undefined} aria-hidden />
      Aktualisieren
    </Button>
  )
}
