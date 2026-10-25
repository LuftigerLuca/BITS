import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addEmbassyMember,
  assignEmbassyThread,
  getEmbassies,
  removeEmbassyMember,
  searchEmbassyMembers,
  setEmbassySettings,
  unassignEmbassyThread,
  type EmbassySettings,
} from '@/lib/api'
import { notify } from '@/lib/notify'
import { errorMessage } from './labels'

const EMBASSIES = ['embassies'] as const

/** Who is in a thread changes with every sync, which a change on this page starts. */
export function useEmbassies() {
  return useQuery({ queryKey: [...EMBASSIES, 'overview'], queryFn: getEmbassies, refetchInterval: 30_000 })
}

/** Only from two characters on, so a single letter does not list half the Discord. */
export function useMemberSearch(query: string) {
  const text = query.trim()
  return useQuery({
    queryKey: [...EMBASSIES, 'member-search', text],
    queryFn: () => searchEmbassyMembers(text),
    enabled: text.length >= 2,
  })
}

/** A mutation of the embassy page that reloads it and tells the result in a toast. */
function useEmbassyMutation<T>(mutationFn: (variables: T) => Promise<void>, success: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      notify.success(success, 'Discord wird gleich abgeglichen.')
      return queryClient.invalidateQueries({ queryKey: EMBASSIES })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

export function useSetEmbassySettings() {
  return useEmbassyMutation((settings: EmbassySettings) => setEmbassySettings(settings), 'Einstellungen gespeichert')
}

export function useAssignThread() {
  return useEmbassyMutation(
    ({ threadId, countryId }: { threadId: string; countryId: string }) => assignEmbassyThread(threadId, countryId),
    'Thread zugeordnet',
  )
}

export function useUnassignThread() {
  return useEmbassyMutation((threadId: string) => unassignEmbassyThread(threadId), 'Zuordnung aufgehoben')
}

export function useAddMember() {
  return useEmbassyMutation(
    ({ threadId, discordUserId }: { threadId: string; discordUserId: string }) => addEmbassyMember(threadId, discordUserId),
    'Mitglied hinzugefügt',
  )
}

export function useRemoveMember() {
  return useEmbassyMutation(
    ({ threadId, discordUserId }: { threadId: string; discordUserId: string }) =>
      removeEmbassyMember(threadId, discordUserId),
    'Mitglied entfernt',
  )
}
