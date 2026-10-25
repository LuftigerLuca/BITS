import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  closeVote,
  createVote,
  getVoteAccess,
  getVoteManagerRoles,
  getVoteOptions,
  getVotes,
  setVoteManagerRoles,
  type CreateVote,
} from '@/lib/api'

const VOTES = ['votes'] as const

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
