import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getHoneypot, resumeHoneypotKicks, setHoneypotChannel } from './api'
import { saveErrorMessage } from './labels'

const HONEYPOT = ['honeypot'] as const

export function useHoneypot() {
  return useQuery({ queryKey: HONEYPOT, queryFn: getHoneypot })
}

export function useSetHoneypotChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (channelId: string | null) => setHoneypotChannel(channelId),
    onSuccess: (_, channelId) => {
      if (channelId === null) notify.success('Honeypot entfernt', 'Der Bot entfernt seine Warnung in Kürze.')
      else notify.success('Honeypot festgelegt', 'Der Bot überwacht den Kanal in spätestens 30 Sekunden.')
      return queryClient.invalidateQueries({ queryKey: HONEYPOT })
    },
    onError: (error) => notify.error(saveErrorMessage(error)),
  })
}

export function useResumeHoneypotKicks() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: resumeHoneypotKicks,
    onSuccess: () => {
      notify.success('Kicks fortgesetzt', 'Der Bot kickt in spätestens 30 Sekunden wieder.')
      return queryClient.invalidateQueries({ queryKey: HONEYPOT })
    },
    onError: (error) => notify.error(saveErrorMessage(error)),
  })
}
