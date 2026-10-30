import type { ComponentProps, ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

/**
 * The action of a row, e.g. editing or removing it: a ghost icon button whose [label] is its accessible name and
 * shows as tooltip on hover and focus. Actions of a whole section are text buttons instead.
 */
export function IconAction({
  label,
  children,
  ...props
}: { label: string; children: ReactNode } & Omit<ComponentProps<typeof Button>, 'aria-label' | 'children'>) {
  return (
    <Tooltip>
      <TooltipTrigger render={<Button type="button" variant="ghost" size="icon-sm" aria-label={label} {...props} />}>
        {children}
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}
