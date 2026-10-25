import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getOverview, setOverviewCountry } from '@/lib/api'

const OVERVIEW = ['overview'] as const

export function useOverview() {
  return useQuery({ queryKey: OVERVIEW, queryFn: getOverview })
}

export function useSetOverviewCountry() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (countryId: string) => setOverviewCountry(countryId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: OVERVIEW }),
  })
}
