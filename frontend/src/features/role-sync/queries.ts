import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRoleRule,
  deleteRoleRule,
  getCountries,
  getRoleRules,
  getRoles,
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

/** The countries of WarEra hardly ever change, so they are loaded once per visit, and only when needed. */
export function useCountries(enabled: boolean) {
  return useQuery({ queryKey: ['countries'], queryFn: getCountries, enabled, staleTime: Infinity })
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
