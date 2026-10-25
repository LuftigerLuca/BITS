import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRoleRule,
  deleteRoleRule,
  getNicknameSetting,
  getRoleRules,
  getRoles,
  setNicknameSetting,
  type CreateRoleRule,
} from '@/lib/api'

/** Everything below changes together: a new or deleted rule changes which roles are managed. */
const ROLE_SYNC = ['role-sync'] as const

export function useRoles() {
  return useQuery({ queryKey: [...ROLE_SYNC, 'roles'], queryFn: getRoles })
}

export function useRoleRules() {
  return useQuery({ queryKey: [...ROLE_SYNC, 'rules'], queryFn: getRoleRules })
}

export function useCreateRoleRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (rule: CreateRoleRule) => createRoleRule(rule),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ROLE_SYNC }),
  })
}

export function useDeleteRoleRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteRoleRule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ROLE_SYNC }),
  })
}

const NICKNAMES = [...ROLE_SYNC, 'nicknames'] as const

export function useNicknameSetting() {
  return useQuery({ queryKey: NICKNAMES, queryFn: getNicknameSetting })
}

export function useSetNicknameSetting() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (enabled: boolean) => setNicknameSetting(enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: NICKNAMES }),
  })
}
