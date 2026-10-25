import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getOverview, setOverviewCountry } from '@/lib/api'
import { RESULT_IMAGE } from '@/features/votes/queries'

const OVERVIEW = ['overview'] as const

export function useOverview() {
  return useQuery({ queryKey: OVERVIEW, queryFn: getOverview })
}

export function useSetOverviewCountry() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (countryId: string) => setOverviewCountry(countryId),
    // The party colours are those of the country's parties.
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: OVERVIEW }),
        queryClient.invalidateQueries({ queryKey: RESULT_IMAGE }),
      ]),
  })
}
