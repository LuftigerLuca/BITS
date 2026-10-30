import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getBotPresence, setBotPresence, type BotPresence } from './api'
import { saveErrorMessage } from './labels'

const BOT_PRESENCE = ['bot-presence'] as const

export function useBotPresence() {
  return useQuery({ queryKey: BOT_PRESENCE, queryFn: getBotPresence })
}

export function useSetBotPresence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (presence: BotPresence) => setBotPresence(presence),
    onSuccess: () => {
      notify.success('Bot-Status gespeichert', 'Discord zeigt ihn in ein paar Sekunden.')
      return queryClient.invalidateQueries({ queryKey: BOT_PRESENCE })
    },
    onError: (error) => notify.error(saveErrorMessage(error)),
  })
}
