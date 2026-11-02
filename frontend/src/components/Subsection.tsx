import { useId, type ReactNode } from 'react'
import { ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'

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

/**
 * A [Subsection] that starts closed and shows its content once opened; description and actions stay visible. The
 * content is only mounted while open, so a dialog an action opens belongs outside of it.
 */
export function CollapsibleSubsection({
  title,
  label,
  description,
  actions,
  children,
}: {
  title: ReactNode
  label?: string
  description?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  const titleId = useId()
  return (
    <Collapsible
      render={<Card size="sm" role="region" aria-label={label} aria-labelledby={label ? undefined : titleId} />}
    >
      <CardHeader>
        <CardTitle as="h3" id={titleId}>
          <CollapsibleTrigger
            render={
              <Button
                variant="ghost"
                size="sm"
                className="group -ml-2.5 h-auto min-h-7 justify-start text-left whitespace-normal"
              />
            }
          >
            <ChevronRightIcon className="transition-transform group-data-[panel-open]:rotate-90" aria-hidden />
            {title}
          </CollapsibleTrigger>
        </CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
        {actions && <CardAction className="flex flex-wrap items-center justify-end gap-2">{actions}</CardAction>}
      </CardHeader>
      <CollapsibleContent>
        <CardContent className="flex flex-col gap-4">{children}</CardContent>
      </CollapsibleContent>
    </Collapsible>
  )
}
