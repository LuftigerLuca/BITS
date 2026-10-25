import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { expectToast } from '@/test/toast'
import { notify } from './notify'

afterEach(() => {
  vi.useRealTimers()
})

/** The class list of the toast's icon, whose shape and color tell the type. */
function icon(toast: HTMLElement) {
  return toast.querySelector('[data-icon] svg')?.getAttribute('class') ?? ''
}

describe('toasts', () => {
  it('tells success, warning and error apart by the shape and color of the icon', async () => {
    render(<Toaster />)
    act(() => {
      notify.success('Gespeichert')
      notify.warning('Nur teilweise gespeichert', 'Zwei Rollen fehlen.')
      notify.error('Nicht gespeichert')
    })

    const success = await expectToast('success', 'Gespeichert')
    const warning = await expectToast('warning', 'Nur teilweise gespeichert')
    const error = await expectToast('error', 'Nicht gespeichert')

    expect(within(warning).getByText('Zwei Rollen fehlen.')).toBeInTheDocument()
    expect(icon(success)).toMatch(/lucide-circle-check.*text-green/)
    expect(icon(warning)).toMatch(/lucide-triangle-alert.*text-amber/)
    expect(icon(error)).toMatch(/lucide-circle-x.*text-destructive/)
  })

  it('closes success and warning after a few seconds and keeps errors until they are closed', async () => {
    vi.useFakeTimers()
    render(<Toaster />)
    act(() => {
      notify.success('Gespeichert')
      notify.warning('Nur teilweise gespeichert')
      notify.error('Nicht gespeichert')
    })

    await act(() => vi.advanceTimersByTimeAsync(10_000))

    expect(screen.queryByText('Gespeichert')).not.toBeInTheDocument()
    expect(screen.queryByText('Nur teilweise gespeichert')).not.toBeInTheDocument()
    const error = screen.getByText('Nicht gespeichert').closest('[data-sonner-toast]') as HTMLElement

    vi.useRealTimers()
    await userEvent.click(within(error).getByRole('button', { name: 'Schließen' }))
    await vi.waitFor(() => expect(screen.queryByText('Nicht gespeichert')).not.toBeInTheDocument())
  })

  it('pauses the timer while the pointer is on the toasts', async () => {
    vi.useFakeTimers()
    render(<Toaster />)
    act(() => {
      notify.success('Gespeichert')
    })
    // Sonner shows the toast after a tick.
    await act(() => vi.advanceTimersByTimeAsync(0))

    fireEvent.mouseEnter(screen.getByText('Gespeichert'))
    await act(() => vi.advanceTimersByTimeAsync(10_000))
    expect(screen.getByText('Gespeichert')).toBeInTheDocument()

    fireEvent.mouseLeave(screen.getByText('Gespeichert'))
    await act(() => vi.advanceTimersByTimeAsync(10_000))
    expect(screen.queryByText('Gespeichert')).not.toBeInTheDocument()
  })

  it('announces toasts to screen readers', async () => {
    render(<Toaster />)
    act(() => {
      notify.success('Gespeichert')
    })

    const toast = await expectToast('success', 'Gespeichert')
    expect(toast.closest('[aria-live]')).toHaveAttribute('aria-live', 'polite')
  })
})
