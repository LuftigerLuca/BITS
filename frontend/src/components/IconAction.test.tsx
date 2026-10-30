import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrashIcon } from 'lucide-react'
import { describe, expect, it, vi } from 'vitest'
import { IconAction } from './IconAction'

describe('IconAction', () => {
  it('is named by its label and shows it as tooltip on hover and focus', async () => {
    const onClick = vi.fn()
    render(
      <IconAction label="Regel löschen" onClick={onClick}>
        <TrashIcon />
      </IconAction>,
    )

    const button = screen.getByRole('button', { name: 'Regel löschen' })
    const tooltip = () => document.querySelector('[data-slot=tooltip-content]')
    expect(tooltip()).toBeNull()
    await userEvent.hover(button)
    await vi.waitFor(() => expect(tooltip()).toHaveTextContent('Regel löschen'))
    await userEvent.unhover(button)
    await vi.waitFor(() => expect(tooltip()).toBeNull())
    await userEvent.tab()
    expect(button).toHaveFocus()
    await vi.waitFor(() => expect(tooltip()).toHaveTextContent('Regel löschen'))

    await userEvent.click(button)
    expect(onClick).toHaveBeenCalledOnce()
  })
})
