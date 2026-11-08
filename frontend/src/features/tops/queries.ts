import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getTopSettings, setTopForums, type TopForumSetting } from './api'
import { saveErrorMessage } from './labels'

const TOPS = ['tops'] as const

export function useTopSettings() {
  return useQuery({ queryKey: TOPS, queryFn: getTopSettings })
}

export function useSetTopForums() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (forums: TopForumSetting[]) => setTopForums(forums),
    onSuccess: () => {
      notify.success('TOP-Foren gespeichert')
      return queryClient.invalidateQueries({ queryKey: TOPS })
    },
    onError: (error) => notify.error(saveErrorMessage(error)),
  })
}
