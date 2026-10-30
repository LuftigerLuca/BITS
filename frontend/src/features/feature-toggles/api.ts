import { request } from '@/lib/api'

/** Parts of BITS an admin can switch off under "Verwaltung"; a feature is on until then. */
export type Feature = 'VERIFICATION' | 'ROLE_SYNC' | 'NICKNAMES' | 'PROFILE' | 'VOTES' | 'EMBASSIES'

export interface FeatureState {
  feature: Feature
  enabled: boolean
}

export async function getFeatures(): Promise<FeatureState[]> {
  return (await request('/web/features')).json()
}

export async function setFeature(feature: Feature, enabled: boolean): Promise<void> {
  await request(`/web/admin/features/${feature}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}
