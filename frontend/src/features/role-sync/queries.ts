import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRoleRule,
  deleteRoleRule,
  getParties,
  getRoleRules,
  getRoles,
  getRemovalLimit,
  getStatusRoles,
  previewDeleteRoleRule,
  previewRoleRule,
  setRemovalLimit,
  setStatusRoles,
  updateRoleRule,
  type CreateRoleRule,
} from '@/lib/api'
import { notify } from '@/lib/notify'
import { errorMessage } from './labels'

/** Everything below changes together: a new, edited or deleted rule changes which roles are managed. */
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

export function useStatusRoles() {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'status-roles'],
    queryFn: getStatusRoles,
  })
}

export function useSetStatusRoles() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: setStatusRoles,
    onSuccess: () => {
      notify.success('Statusrollen gespeichert', 'Discord wird gleich abgeglichen.')
      return queryClient.invalidateQueries({ queryKey: ROLE_SYNC })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

export function useRemovalLimit() {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'removal-limit'],
    queryFn: getRemovalLimit,
  })
}

export function useSetRemovalLimit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: setRemovalLimit,
    onSuccess: (_, enabled) => {
      notify.success(
        enabled ? 'Schutz vor Massenentzug eingeschaltet' : 'Schutz vor Massenentzug ausgeschaltet',
        enabled ? undefined : 'Discord wird gleich abgeglichen.',
      )
      return queryClient.invalidateQueries({ queryKey: ROLE_SYNC })
    },
    onError: (error) => notify.error(errorMessage(error)),
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

export function useUpdateRoleRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (rule: CreateRoleRule) => updateRoleRule(rule),
    onSuccess: () => {
      notify.success('Regel gespeichert')
      return queryClient.invalidateQueries({ queryKey: ROLE_SYNC })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

export function useDeleteRoleRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (roleId: string) => deleteRoleRule(roleId),
    onSuccess: () => {
      notify.success('Regel gelöscht')
      return queryClient.invalidateQueries({ queryKey: ROLE_SYNC })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

/**
 * What saving [rule] as the rule of its role would change, new or edited, loaded while the confirmation is open. Always
 * fresh: the members' roles and the WarEra data change all the time.
 */
export function useRulePreview(rule: CreateRoleRule | null) {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'preview', 'save', rule],
    queryFn: () => previewRoleRule(rule!),
    enabled: rule !== null,
    staleTime: 0,
    gcTime: 0,
  })
}

/** Like [useRulePreview], for deleting the rule of [roleId]. */
export function useDeletionPreview(roleId: string | null) {
  return useQuery({
    queryKey: [...ROLE_SYNC, 'preview', 'delete', roleId],
    queryFn: () => previewDeleteRoleRule(roleId!),
    enabled: roleId !== null,
    staleTime: 0,
    gcTime: 0,
  })
}
