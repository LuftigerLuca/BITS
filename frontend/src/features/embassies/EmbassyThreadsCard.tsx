import { useState } from 'react'
import { PencilLineIcon } from 'lucide-react'
import { EmptyState } from '@/components/QueryState'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { Embassies } from './api'
import { AssignForm } from './AssignForm'
import { CreationSection } from './CreationSection'
import { useRenameThreads } from './queries'
import { ThreadSection } from './ThreadSection'

/** The embassy threads by country, who is in each and why, and the form to assign another one. */
export function EmbassyThreadsCard({ embassies }: { embassies: Embassies }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Botschafts-Threads</CardTitle>
        <CardDescription>
          Wähle einen privaten Thread aus Discord oder lass ihn vom Bot im Botschafts-Kanal anlegen. Der Bot braucht im
          Kanal die Rechte „Threads verwalten“ und „Nachrichten in Threads senden“. BITS gleicht die Threads nach jeder
          Verknüpfung und stündlich ab und entfernt alle, die weder ein Amt haben noch manuell hinzugefügt sind, außer
          wer Threads verwalten darf.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {embassies.threads.length > 0 && <RenameButton template={embassies.settings.threadNameTemplate} />}
        {embassies.creations.map((creation) => (
          <CreationSection key={creation.id} creation={creation} />
        ))}
        {embassies.threads.length === 0 && embassies.creations.length === 0 ? (
          <EmptyState
            title="Noch keinem Land ist ein Thread zugeordnet"
            description="Ordne unten einen Thread zu oder lass ihn vom Bot anlegen."
          />
        ) : (
          embassies.threads.map((thread) => <ThreadSection key={thread.threadId} thread={thread} />)
        )}
        <AssignForm embassies={embassies} />
      </CardContent>
    </Card>
  )
}

/** Has the bot rename the assigned threads after the template, once confirmed. */
function RenameButton({ template }: { template: string }) {
  const rename = useRenameThreads()
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <Button variant="outline" size="sm" className="self-start" onClick={() => setConfirming(true)}>
        <PencilLineIcon />
        Bestehende umbenennen
      </Button>
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Bestehende Threads umbenennen?</AlertDialogTitle>
            <AlertDialogDescription>
              Der Bot benennt alle zugeordneten Threads nach der Vorlage „{template}“ um. Archivierte Threads
              überspringt er, Threads mit passendem Namen bleiben unberührt.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <Button
              disabled={rename.isPending}
              onClick={() => rename.mutate(undefined, { onSuccess: () => setConfirming(false) })}
            >
              Umbenennen
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
