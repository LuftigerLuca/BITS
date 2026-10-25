import { useRef, useState, type ChangeEvent } from 'react'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { MAX_BACKGROUND_BYTES, resultPreviewUrl } from '@/lib/api'
import { BACKGROUND_TOO_LARGE, backgroundErrorMessage } from './labels'
import { useResultSettings, useSetResultBackground } from './queries'

const TYPES = ['image/png', 'image/jpeg']

/** Admins only: the background of the result image, with a preview that also shows the party colours. */
export function ResultImageCard() {
  const settings = useResultSettings()
  const setBackground = useSetResultBackground()
  const input = useRef<HTMLInputElement>(null)
  // Checked before uploading, so a large file is not sent for nothing.
  const [rejected, setRejected] = useState<string | null>(null)

  function choose(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (file.size > MAX_BACKGROUND_BYTES) {
      setRejected(BACKGROUND_TOO_LARGE)
    } else if (!TYPES.includes(file.type)) {
      setRejected('Nur PNG- und JPEG-Bilder sind möglich.')
    } else {
      setRejected(null)
      setBackground.mutate(file)
    }
  }

  const error = rejected ?? (setBackground.isError ? backgroundErrorMessage(setBackground.error) : null)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Bild der Auswertung</CardTitle>
        <CardDescription>
          BITS postet das Ergebnis einer Abstimmung als Bild. Ein eigener Hintergrund wird zugeschnitten und
          abgedunkelt, damit Text und Säulen lesbar bleiben. Änderungen gelten ab der nächsten Auswertung und im Archiv.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {settings.isPending ? (
          <LoadingState label="Einstellungen werden geladen …" />
        ) : settings.isError ? (
          <ErrorState message={backgroundErrorMessage(settings.error)} onRetry={() => settings.refetch()} />
        ) : (
          <>
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <p className="text-sm">
              Hintergrund: {settings.data.customBackground ? 'eigenes Bild' : 'BITS-Adler'}
            </p>
            <div className="flex flex-wrap gap-2">
              <input
                ref={input}
                type="file"
                accept={TYPES.join(',')}
                className="sr-only"
                aria-label="Hintergrundbild"
                onChange={choose}
              />
              <Button type="button" disabled={setBackground.isPending} onClick={() => input.current?.click()}>
                Bild hochladen
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={setBackground.isPending || !settings.data.customBackground}
                onClick={() => {
                  setRejected(null)
                  setBackground.mutate(null)
                }}
              >
                Auf Adler zurücksetzen
              </Button>
            </div>
            <p className="text-sm text-muted-foreground">PNG oder JPEG, höchstens 5 MB.</p>
            <figure className="flex flex-col gap-2">
              <img
                src={resultPreviewUrl(settings.dataUpdatedAt)}
                alt="Vorschau des Ergebnisbilds mit Beispieldaten"
                className="w-full max-w-3xl rounded-md border"
              />
              <figcaption className="text-sm text-muted-foreground">Vorschau mit Beispieldaten</figcaption>
            </figure>
          </>
        )}
      </CardContent>
    </Card>
  )
}
