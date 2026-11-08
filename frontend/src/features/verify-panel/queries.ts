import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getVerifyPanel, setVerifyPanelChannel } from './api'
import { saveErrorMessage } from './labels'

const VERIFY_PANEL = ['verify-panel'] as const

export function useVerifyPanel() {
  return useQuery({ queryKey: VERIFY_PANEL, queryFn: getVerifyPanel })
}

export function useSetVerifyPanelChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (channelId: string | null) => setVerifyPanelChannel(channelId),
    onSuccess: (_, channelId) => {
      if (channelId === null) notify.success('Verifizierungs-Kanal entfernt', 'Der Bot löscht seine Nachricht in Kürze.')
      else notify.success('Verifizierungs-Kanal festgelegt', 'Der Bot postet seine Nachricht in spätestens 30 Sekunden.')
      return queryClient.invalidateQueries({ queryKey: VERIFY_PANEL })
    },
    onError: (error) => notify.error(saveErrorMessage(error)),
  })
}
