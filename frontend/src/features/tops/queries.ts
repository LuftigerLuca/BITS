import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { getTopManagerRoles, getTopSettings, setTopForums, setTopManagerRoles, type TopForumSetting } from './api'
import { rolesSaveErrorMessage, saveErrorMessage } from './labels'

const TOPS = ['tops'] as const
const SETTINGS = [...TOPS, 'settings'] as const
const MANAGER_ROLES = [...TOPS, 'manager-roles'] as const

export function useTopSettings() {
  return useQuery({ queryKey: SETTINGS, queryFn: getTopSettings })
}

export function useSetTopForums() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (forums: TopForumSetting[]) => setTopForums(forums),
    onSuccess: () => {
      notify.success('TOP-Foren gespeichert')
      return queryClient.invalidateQueries({ queryKey: SETTINGS })
    },
    onError: (error) => notify.error(saveErrorMessage(error)),
  })
}

export function useTopManagerRoles() {
  return useQuery({ queryKey: MANAGER_ROLES, queryFn: getTopManagerRoles })
}

export function useSetTopManagerRoles() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (roleIds: string[]) => setTopManagerRoles(roleIds),
    onSuccess: () => {
      notify.success('Rollen gespeichert')
      return queryClient.invalidateQueries({ queryKey: MANAGER_ROLES })
    },
    onError: (error) => notify.error(rolesSaveErrorMessage(error)),
  })
}
