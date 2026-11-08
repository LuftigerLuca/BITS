import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getReprimands, getReprimandSettings, setReprimandSettings, withdrawReprimand } from './api'
import { settingsSaveErrorMessage, withdrawErrorMessage } from './labels'

const REPRIMANDS = ['reprimands'] as const
const LIST = [...REPRIMANDS, 'list'] as const
const SETTINGS = [...REPRIMANDS, 'settings'] as const

/** Reads the database only, and new reprimands come from Discord while the page is open. */
export function useReprimands() {
  return useQuery({ queryKey: LIST, queryFn: getReprimands, refetchOnWindowFocus: true })
}

export function useWithdrawReprimand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => withdrawReprimand(id),
    onSuccess: () => {
      notify.success('Rüge zurückgenommen')
      return queryClient.invalidateQueries({ queryKey: LIST })
    },
    onError: (error) => notify.error(withdrawErrorMessage(error)),
  })
}

export function useReprimandSettings() {
  return useQuery({ queryKey: SETTINGS, queryFn: getReprimandSettings })
}

export function useSetReprimandSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (settings: { roleIds: string[]; timeoutMinutes: number }) => setReprimandSettings(settings),
    onSuccess: () => {
      notify.success('Einstellungen gespeichert')
      return queryClient.invalidateQueries({ queryKey: SETTINGS })
    },
    onError: (error) => notify.error(settingsSaveErrorMessage(error)),
  })
}
