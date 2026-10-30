import type { ReactNode } from 'react'
import { useDocumentTitle } from '@/hooks/use-document-title'

/**
 * Title of a dashboard page, which also names the browser tab. The `Layout` moves the focus here after a navigation,
 * hence `tabIndex={-1}`.
 */
export function PageHeader({ title, description }: { title: string; description?: ReactNode }) {
  useDocumentTitle(title)
  return (
    <header className="flex flex-col gap-1">
      <h1 tabIndex={-1} className="font-heading text-xl font-semibold outline-none">
        {title}
      </h1>
      {description && <p className="text-sm text-muted-foreground">{description}</p>}
    </header>
  )
}
