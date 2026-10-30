import { useState } from 'react'
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
import { Input } from '@/components/ui/input'
import { Field, FieldLabel } from '@/components/ui/field'
import { MAX_THREAD_NAME_LENGTH } from './labels'
import { useCreateThread } from './queries'

/** Asks for the name of the thread the bot creates, the one after the template unless changed; mounted while open. */
export function CreateDialog({
  countryId,
  countryName,
  initialName,
  onClose,
  onCreated,
}: {
  countryId: string
  countryName: string
  initialName: string
  onClose: () => void
  onCreated?: () => void
}) {
  const create = useCreateThread()
  const [name, setName] = useState(initialName)
  const trimmed = name.trim()
  return (
    <AlertDialog open onOpenChange={(next) => !next && onClose()}>
      <AlertDialogContent>
        <form
          className="contents"
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate(
              { countryId, threadName: trimmed },
              {
                onSuccess: () => {
                  onClose()
                  onCreated?.()
                },
              },
            )
          }}
        >
          <AlertDialogHeader>
            <AlertDialogTitle>Thread für {countryName} anlegen</AlertDialogTitle>
            <AlertDialogDescription>
              Der Bot legt im Botschafts-Kanal einen privaten Thread an und ordnet ihn {countryName} zu.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <Field>
            <FieldLabel htmlFor={`embassy-create-name-${countryId}`}>Name</FieldLabel>
            <Input
              id={`embassy-create-name-${countryId}`}
              value={name}
              maxLength={MAX_THREAD_NAME_LENGTH}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <Button type="submit" disabled={trimmed.length === 0 || create.isPending}>
              Anlegen
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}
