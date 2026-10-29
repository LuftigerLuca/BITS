import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addDskMilitaryUnit,
  createDsk,
  deleteDsk,
  getMilitaryUnits,
  removeDskMilitaryUnit,
  searchDskMilitaryUnits,
  updateDsk,
  type DskRequest,
} from '@/lib/api'
import { notify } from '@/lib/notify'
import { errorMessage } from './labels'

const MILITARY_UNITS = ['military-units'] as const

export function useMilitaryUnits() {
  return useQuery({ queryKey: [...MILITARY_UNITS, 'overview'], queryFn: getMilitaryUnits })
}

/** Searched on demand only: each match costs the backend a WarEra call. */
export function useMilitaryUnitSearch(query: string | null) {
  return useQuery({
    queryKey: [...MILITARY_UNITS, 'search', query],
    queryFn: () => searchDskMilitaryUnits(query!),
    enabled: query !== null,
  })
}

/**
 * A mutation of the page that reloads it and the roles of the rules, whose roles a DSK may take, and tells the result
 * in a toast.
 */
function useDskMutation<T>(mutationFn: (variables: T) => Promise<void>, success: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      notify.success(success)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: MILITARY_UNITS }),
        queryClient.invalidateQueries({ queryKey: ['role-sync'] }),
      ])
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

export function useCreateDsk() {
  return useDskMutation((dsk: DskRequest) => createDsk(dsk), 'DSK angelegt')
}

export function useUpdateDsk() {
  return useDskMutation(({ id, dsk }: { id: number; dsk: DskRequest }) => updateDsk(id, dsk), 'DSK gespeichert')
}

export function useDeleteDsk() {
  return useDskMutation((id: number) => deleteDsk(id), 'DSK gelöscht')
}

export function useAddMilitaryUnit() {
  return useDskMutation(
    ({ dskId, muId }: { dskId: number; muId: string }) => addDskMilitaryUnit(dskId, muId),
    'MU zugeordnet',
  )
}

export function useRemoveMilitaryUnit() {
  return useDskMutation(
    ({ dskId, muId }: { dskId: number; muId: string }) => removeDskMilitaryUnit(dskId, muId),
    'MU entfernt',
  )
}
