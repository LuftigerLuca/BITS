import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getOverview, getOverviewHistory, setOverviewCountry } from './api'
import { notify } from '@/lib/notify'
import { RESULT_IMAGE } from '@/features/votes/queries'
import { countryErrorMessage } from './labels'

const OVERVIEW = ['overview'] as const

export function useOverview() {
  return useQuery({ queryKey: OVERVIEW, queryFn: getOverview })
}

export function useOverviewHistory(days: number) {
  return useQuery({ queryKey: [...OVERVIEW, 'history', days], queryFn: () => getOverviewHistory(days) })
}

export function useSetOverviewCountry() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (countryId: string) => setOverviewCountry(countryId),
    // The party colours are those of the country's parties.
    onSuccess: () => {
      notify.success('Land gespeichert')
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: OVERVIEW }),
        queryClient.invalidateQueries({ queryKey: RESULT_IMAGE }),
      ])
    },
    onError: (error) => notify.error(countryErrorMessage(error)),
  })
}
