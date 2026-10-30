import { useState } from 'react'
import { Subsection } from '@/components/Subsection'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { EmbassyCreation } from './api'
import { CreateDialog } from './CreateDialog'
import { useDismissCreation } from './queries'

/** A thread the bot is creating, or why it could not, with the way to try again. */
export function CreationSection({ creation }: { creation: EmbassyCreation }) {
  const dismiss = useDismissCreation()
  const [retrying, setRetrying] = useState(false)
  return (
    <Subsection
      title={creation.countryName}
      label={`Botschaft ${creation.countryName}`}
      description={creation.threadName}
      actions={
        creation.failure === null ? (
          <Badge variant="secondary">Wird angelegt …</Badge>
        ) : (
          <>
            <Badge variant="destructive">Anlegen fehlgeschlagen</Badge>
            <Button variant="outline" size="sm" onClick={() => setRetrying(true)}>
              Automatisch anlegen
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={dismiss.isPending}
              onClick={() => dismiss.mutate(creation.id)}
            >
              Verwerfen
            </Button>
          </>
        )
      }
    >
      {creation.failure !== null && <p className="text-sm text-destructive">{creation.failure}</p>}
      {retrying && (
        <CreateDialog
          countryId={creation.countryId}
          countryName={creation.countryName}
          initialName={creation.threadName}
          onClose={() => setRetrying(false)}
        />
      )}
    </Subsection>
  )
}
