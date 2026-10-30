import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getMember, getMemberRoles, searchMembers, syncMember, unlinkMember } from './api'
import { UNLINK_ERROR, syncErrorMessage } from './labels'

const MEMBERS = ['members'] as const

/** Nothing is searched for an empty query. */
export function useMemberSearch(query: string) {
  return useQuery({
    queryKey: [...MEMBERS, 'search', query],
    queryFn: () => searchMembers(query),
    enabled: query.trim() !== '',
    staleTime: 0,
  })
}

/** The system log grows all the time, so it is loaded anew on every visit. */
export function useMember(discordUserId: string) {
  return useQuery({
    queryKey: [...MEMBERS, 'detail', discordUserId],
    queryFn: () => getMember(discordUserId),
    staleTime: 0,
  })
}

/** Judged with fresh WarEra data on every visit, like `/rollen`. */
export function useMemberRoles(discordUserId: string) {
  return useQuery({
    queryKey: [...MEMBERS, 'roles', discordUserId],
    queryFn: () => getMemberRoles(discordUserId),
    staleTime: 0,
  })
}

export function useSyncMember() {
  return useMutation({
    mutationFn: syncMember,
    onSuccess: () => notify.success('Abgleich angestoßen', 'Das Ergebnis erscheint gleich im System-Log.'),
    onError: (error) => notify.error(syncErrorMessage(error)),
  })
}

export function useUnlinkMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: unlinkMember,
    onSuccess: () => {
      notify.success(
        'Verknüpfung gelöst',
        'Die Rollen, die BITS verwaltet, verliert das Mitglied beim folgenden Abgleich.',
      )
      return queryClient.invalidateQueries({ queryKey: MEMBERS })
    },
    onError: () => notify.error(UNLINK_ERROR),
  })
}
