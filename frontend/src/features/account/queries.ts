import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/auth/context'
import { getAccount, getAccountRoles, unlinkAccount } from '@/lib/api'

/** Link and roles change together: without a link, no role is met. */
const ACCOUNT = ['account'] as const

export function useAccount() {
  return useQuery({ queryKey: [...ACCOUNT, 'overview'], queryFn: getAccount })
}

/** Judged with fresh WarEra data on every visit, like `/rollen`. */
export function useAccountRoles() {
  return useQuery({ queryKey: [...ACCOUNT, 'roles'], queryFn: getAccountRoles, staleTime: 0 })
}

/** The session knows the WarEra name as well, e.g. for the start page, so it is checked again. */
export function useUnlinkAccount() {
  const queryClient = useQueryClient()
  const { refresh } = useAuth()
  return useMutation({
    mutationFn: unlinkAccount,
    onSuccess: () => Promise.all([queryClient.invalidateQueries({ queryKey: ACCOUNT }), refresh()]),
  })
}
