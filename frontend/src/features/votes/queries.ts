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
} from './api'
import { notify } from '@/lib/notify'
import { backgroundErrorMessage, errorMessage, partyColorsErrorMessage } from './labels'

const VOTES = ['votes'] as const

const CHANNEL_CONFLICT =
  'Der Bot kann in diesem Kanal nicht posten. Prüfe seine Rechte dort und versuche es in ein paar Sekunden noch einmal.'

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
    onSuccess: () => {
      notify.success('Abstimmung gestartet', 'Sie erscheint gleich im Kanal.')
      return queryClient.invalidateQueries({ queryKey: VOTES })
    },
    onError: (error) => notify.error(errorMessage(error, CHANNEL_CONFLICT)),
  })
}

export function useCloseVote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, cancel }: { id: number; cancel: boolean }) => closeVote(id, cancel),
    onSuccess: (_, { cancel }) => {
      if (cancel) notify.success('Abstimmung abgebrochen')
      else notify.success('Abstimmung beendet', 'Die Auswertung erscheint gleich im Kanal.')
      return queryClient.invalidateQueries({ queryKey: VOTES })
    },
    onError: (error) => notify.error(errorMessage(error)),
  })
}

export function useSetVoteManagerRoles() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (roleIds: string[]) => setVoteManagerRoles(roleIds),
    onSuccess: () => {
      notify.success('Rollen gespeichert')
      return queryClient.invalidateQueries({ queryKey: VOTES })
    },
    onError: (error) => notify.error(errorMessage(error)),
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
    onSuccess: (_, file) => {
      notify.success(file ? 'Hintergrund gespeichert' : 'Auf Adler zurückgesetzt')
      return queryClient.invalidateQueries({ queryKey: RESULT_IMAGE })
    },
    onError: (error) => notify.error(backgroundErrorMessage(error)),
  })
}

export function useSetPartyColors() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ colors, noPartyColor }: { colors: Record<string, string>; noPartyColor: string | null }) =>
      setPartyColors(colors, noPartyColor),
    onSuccess: () => {
      notify.success('Parteifarben gespeichert')
      return queryClient.invalidateQueries({ queryKey: RESULT_IMAGE })
    },
    onError: (error) => notify.error(partyColorsErrorMessage(error)),
  })
}
