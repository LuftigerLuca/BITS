import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  closeVote,
  createVote,
  getVoteAccess,
  getPartyColors,
  getResultSettings,
  getVoteManagerRoles,
  getVoteOptions,
  getVotes,
  resetResultBackground,
  setPartyColors,
  setVoteManagerRoles,
  uploadResultBackground,
  type CreateVote,
} from '@/lib/api'

const VOTES = ['votes'] as const

/** Settings of the result image; the country under "Verwaltung" invalidates them too. */
export const RESULT_IMAGE = [...VOTES, 'result-image'] as const

/** Whether the votes page shows up at all; asked by every user, so it is not under [VOTES]. */
export function useVoteAccess() {
  return useQuery({ queryKey: ['vote-access'], queryFn: getVoteAccess })
}

export function useVotes() {
  // Participation and the evaluation change without the user doing anything.
  return useQuery({ queryKey: [...VOTES, 'list'], queryFn: getVotes, refetchInterval: 30_000 })
}

export function useVoteOptions() {
  return useQuery({ queryKey: [...VOTES, 'options'], queryFn: getVoteOptions })
}

export function useVoteManagerRoles() {
  return useQuery({ queryKey: [...VOTES, 'manager-roles'], queryFn: getVoteManagerRoles })
}

export function useCreateVote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (vote: CreateVote) => createVote(vote),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: VOTES }),
  })
}

export function useCloseVote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, cancel }: { id: number; cancel: boolean }) => closeVote(id, cancel),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: VOTES }),
  })
}

export function useSetVoteManagerRoles() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (roleIds: string[]) => setVoteManagerRoles(roleIds),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: VOTES }),
  })
}

export function useResultSettings() {
  return useQuery({ queryKey: [...RESULT_IMAGE, 'settings'], queryFn: getResultSettings })
}

export function usePartyColors() {
  return useQuery({ queryKey: [...RESULT_IMAGE, 'party-colors'], queryFn: getPartyColors })
}

/** Uploads a file, or resets to the eagle with `null`. */
export function useSetResultBackground() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File | null) => (file ? uploadResultBackground(file) : resetResultBackground()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: RESULT_IMAGE }),
  })
}

export function useSetPartyColors() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ colors, noPartyColor }: { colors: Record<string, string>; noPartyColor: string | null }) =>
      setPartyColors(colors, noPartyColor),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: RESULT_IMAGE }),
  })
}
