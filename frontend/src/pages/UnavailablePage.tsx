import { useState } from 'react'
import { useAuth } from '@/auth/context'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'

/** Shown when the session could not be checked at all, e.g. while BITS is being deployed. */
export function UnavailablePage() {
  const { refresh } = useAuth()
  const [retrying, setRetrying] = useState(false)

  async function retry() {
    setRetrying(true)
    await refresh()
    setRetrying(false)
  }

  return (
    <main className="flex min-h-svh items-center justify-center p-4">
      <Card className="w-full max-w-sm" role="alert">
        <CardHeader>
          <CardTitle>Dashboard gerade nicht erreichbar</CardTitle>
          <CardDescription>
            BITS antwortet gerade nicht, zum Beispiel während eines Updates. Versuche es in ein paar Sekunden noch
            einmal.
          </CardDescription>
        </CardHeader>
        <CardFooter>
          <Button onClick={retry} disabled={retrying}>
            Erneut versuchen
          </Button>
        </CardFooter>
      </Card>
    </main>
  )
}
