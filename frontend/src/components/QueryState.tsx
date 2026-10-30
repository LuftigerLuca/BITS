import type { ReactNode } from 'react'
import { LoaderCircleIcon } from 'lucide-react'
import { Alert, AlertAction, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from '@/components/ui/empty'

/** Placeholder while data loads, so a list never looks empty before it arrived. */
export function LoadingState({ label = 'Wird geladen …' }: { label?: string }) {
  return (
    <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
      <LoaderCircleIcon className="size-4 animate-spin" aria-hidden />
      {label}
    </p>
  )
}

/** Data that could not be loaded, with a way to try again. */
export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Alert variant="destructive">
      <AlertDescription>{message}</AlertDescription>
      <AlertAction>
        <Button variant="outline" size="sm" onClick={onRetry}>
          Erneut versuchen
        </Button>
      </AlertAction>
    </Alert>
  )
}

/**
 * A list that has nothing in it yet. [description] names the action that adds something, if there is one; [action]
 * can hold its button. Short hints inside a row or a subsection stay plain text.
 */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: ReactNode
  action?: ReactNode
}) {
  return (
    <Empty className="border p-4 md:p-6">
      <EmptyHeader>
        <EmptyTitle>{title}</EmptyTitle>
        {description && <EmptyDescription>{description}</EmptyDescription>}
      </EmptyHeader>
      {action && <EmptyContent>{action}</EmptyContent>}
    </Empty>
  )
}
