import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRoleRule,
  deleteRoleRule,
  getParties,
  getRoleRules,
  getRoles,
  previewCreateRoleRule,
  previewDeleteRoleRule,
  type CreateRoleRule,
} from '@/lib/api'
import { notify } from '@/lib/notify'
import { errorMessage } from './labels'

/** Everything below changes together: a new or deleted rule changes which roles are managed. */
const ROLE_SYNC = ['role-sync'] as const

export function useRoles() {
  return useQuery({ queryKey: [...ROLE_SYNC, 'roles'], queryFn: getRoles })
}

export function useRoleRules() {
  return useQuery({ queryKey: [...ROLE_SYNC, 'rules'], queryFn: getRoleRules })
}

/** The parties of [countryId], for party conditions; they change rarely, so they are kept for the visit. */
export function useParties(countryId: string | null) {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'parties', countryId],
    queryFn: () => getParties(countryId!),
    enabled: countryId !== null,
    staleTime: Infinity,
  })
}

export function useCreateRoleRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (rule: CreateRoleRule) => createRoleRule(rule),
    onSuccess: () => {
      notify.success('Regel angelegt')
      return queryClient.invalidateQueries({ queryKey: ROLE_SYNC })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

export function useDeleteRoleRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteRoleRule(id),
    onSuccess: () => {
      notify.success('Regel gelöscht')
      return queryClient.invalidateQueries({ queryKey: ROLE_SYNC })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

/**
 * What creating [rule] would change, loaded while the confirmation is open. Always fresh: the members' roles and the
 * WarEra data change all the time.
 */
export function useCreatePreview(rule: CreateRoleRule | null) {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'preview', 'create', rule],
    queryFn: () => previewCreateRoleRule(rule!),
    enabled: rule !== null,
    staleTime: 0,
    gcTime: 0,
  })
}

/** Like [useCreatePreview], for deleting the rule [id]. */
export function useDeletionPreview(id: number | null) {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'preview', 'delete', id],
    queryFn: () => previewDeleteRoleRule(id!),
    enabled: id !== null,
    staleTime: 0,
    gcTime: 0,
  })
}
