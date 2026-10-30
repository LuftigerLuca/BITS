import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addEmbassyMember,
  assignEmbassyThread,
  createEmbassyThread,
  dismissEmbassyCreation,
  getEmbassies,
  removeEmbassyMember,
  renameEmbassyThreads,
  searchEmbassyMembers,
  setEmbassySettings,
  unassignEmbassyThread,
  type EmbassySettings,
} from './api'
import { notify } from '@/lib/notify'
import { CANNOT_VIEW, errorMessage } from './labels'

const EMBASSIES = ['embassies'] as const

/**
 * Who is in a thread changes with every sync, which a change on this page starts. While the bot creates a thread, the
 * page asks more often, so the thread shows up within seconds.
 */
export function useEmbassies() {
  return useQuery({
    queryKey: [...EMBASSIES, 'overview'],
    queryFn: getEmbassies,
    refetchInterval: (query) => (query.state.data?.creations.some((c) => c.failure === null) ? 3_000 : 30_000),
  })
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
function useEmbassyMutation<T>(
  mutationFn: (variables: T) => Promise<void>,
  success: string,
  cannotView?: string,
  detail: string | undefined = 'Discord wird gleich abgeglichen.',
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      notify.success(success, detail)
      return queryClient.invalidateQueries({ queryKey: EMBASSIES })
    },
    onError: (error) => notify.error(errorMessage(error, cannotView)),
  })
}

/** [cannotView] says what a 422 means for the settings at hand. */
export function useSetEmbassySettings(cannotView?: string) {
  return useEmbassyMutation(
    (settings: EmbassySettings) => setEmbassySettings(settings),
    'Einstellungen gespeichert',
    cannotView,
  )
}

export function useCreateThread() {
  return useEmbassyMutation(
    ({ countryId, threadName }: { countryId: string; threadName: string }) =>
      createEmbassyThread(countryId, threadName),
    'Thread wird angelegt',
    undefined,
    'Der Bot legt ihn gleich an.',
  )
}

export function useDismissCreation() {
  return useEmbassyMutation(
    (creationId: number) => dismissEmbassyCreation(creationId),
    'Verworfen',
    undefined,
    undefined,
  )
}

/** Fire-and-forget: the new names show once the bot reports the threads again. */
export function useRenameThreads() {
  return useEmbassyMutation(
    () => renameEmbassyThreads(),
    'Threads werden umbenannt',
    undefined,
    'Die neuen Namen erscheinen gleich, Fehler im System-Log.',
  )
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
    CANNOT_VIEW.member,
  )
}

export function useRemoveMember() {
  return useEmbassyMutation(
    ({ threadId, discordUserId }: { threadId: string; discordUserId: string }) =>
      removeEmbassyMember(threadId, discordUserId),
    'Mitglied entfernt',
  )
}
