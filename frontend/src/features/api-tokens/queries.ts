import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notify } from '@/lib/notify'
import { createApiToken, getApiTokens, revokeApiToken, type CreateApiTokenRequest } from './api'
import { createErrorMessage, revokeErrorMessage } from './labels'

const API_TOKENS = ['api-tokens'] as const

/** Reads the database only, and "zuletzt benutzt" changes while the page is open. */
export function useApiTokens() {
  return useQuery({ queryKey: API_TOKENS, queryFn: getApiTokens, refetchOnWindowFocus: true })
}

export function useCreateApiToken() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (token: CreateApiTokenRequest) => createApiToken(token),
    onSuccess: () => {
      notify.success('Token angelegt')
      return queryClient.invalidateQueries({ queryKey: API_TOKENS })
    },
    onError: (error) => notify.error(createErrorMessage(error)),
  })
}

export function useRevokeApiToken() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => revokeApiToken(id),
    onSuccess: () => {
      notify.success('Token widerrufen')
      return queryClient.invalidateQueries({ queryKey: API_TOKENS })
    },
    onError: (error) => notify.error(revokeErrorMessage(error)),
  })
}
