import { DownloadIcon } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ACCOUNT_EXPORT_URL } from '@/lib/api'

/** The member's own data request, without asking an admin. */
export function DataCard() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Deine Daten</CardTitle>
        <CardDescription>
          Lade als JSON-Datei herunter, was BITS zu deiner Discord-ID speichert: Verknüpfung, offene Verifizierung,
          Nickname-Einstellung, Pings und Einträge im System-Log. Zustell-Events für den Bot, Server-Logs und Backups
          sind nicht enthalten; sie werden nach festen Fristen gelöscht.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <a href={ACCOUNT_EXPORT_URL} download className={buttonVariants({ variant: 'outline' })}>
          <DownloadIcon aria-hidden />
          Daten herunterladen
        </a>
      </CardContent>
    </Card>
  )
}
