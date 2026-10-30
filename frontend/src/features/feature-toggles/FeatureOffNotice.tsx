import { CirclePauseIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import type { Feature } from './api'
import { useFeatureEnabled } from './queries'

/**
 * Says on a page that its feature is switched off, with [children] telling what that means there. The page itself
 * stays usable, so admins can prepare its settings while the feature is paused.
 */
export function FeatureOffNotice({ feature, children }: { feature: Feature; children: ReactNode }) {
  if (useFeatureEnabled(feature) !== false) return null
  return (
    // A status rather than an alert: it describes the page, nothing just went wrong.
    <Alert role="status">
      <CirclePauseIcon />
      <AlertTitle>Diese Funktion ist gerade ausgeschaltet.</AlertTitle>
      <AlertDescription>{children}</AlertDescription>
    </Alert>
  )
}
