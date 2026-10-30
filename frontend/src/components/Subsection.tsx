import { useId, type ReactNode } from 'react'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * A block inside a card, such as one DSK, one embassy or one condition of a rule: a small card with an `h3` title, an
 * optional description and its actions in the header. It is a region named after its title, or a group for the parts
 * of a form.
 */
export function Subsection({
  title,
  label,
  description,
  actions,
  role = 'region',
  children,
}: {
  title: ReactNode
  /** Names the block if the title alone is not clear, e.g. "Botschaft France" for "France". */
  label?: string
  description?: ReactNode
  actions?: ReactNode
  role?: 'region' | 'group'
  children?: ReactNode
}) {
  const titleId = useId()
  return (
    <Card size="sm" role={role} aria-label={label} aria-labelledby={label ? undefined : titleId}>
      <CardHeader>
        <CardTitle as="h3" id={titleId}>
          {title}
        </CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
        {actions && <CardAction className="flex flex-wrap items-center justify-end gap-2">{actions}</CardAction>}
      </CardHeader>
      {children && <CardContent className="flex flex-col gap-4">{children}</CardContent>}
    </Card>
  )
}
