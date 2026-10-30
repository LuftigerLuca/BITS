import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getFeatures, setFeature, type Feature } from './api'
import { notify } from '@/lib/notify'
import { ERROR_MESSAGE, FEATURES } from './labels'

const FEATURES_KEY = ['features'] as const

/** Asked by every user, since the pages of features that are off say so. */
export function useFeatures() {
  return useQuery({ queryKey: FEATURES_KEY, queryFn: getFeatures })
}

/** `undefined` while the features load or could not be loaded. */
export function useFeatureEnabled(feature: Feature): boolean | undefined {
  return useFeatures().data?.find((state) => state.feature === feature)?.enabled
}

export function useSetFeature() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ feature, enabled }: { feature: Feature; enabled: boolean }) => setFeature(feature, enabled),
    onSuccess: (_, { feature, enabled }) => {
      notify.success(`${FEATURES[feature].label} ${enabled ? 'eingeschaltet' : 'ausgeschaltet'}`)
      return queryClient.invalidateQueries({ queryKey: FEATURES_KEY })
    },
    onError: () => notify.error(ERROR_MESSAGE),
  })
}
