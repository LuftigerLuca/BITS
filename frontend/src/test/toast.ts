import { screen } from '@testing-library/react'
import { expect } from 'vitest'

/** The toast showing [title], once it is there; needs a `Toaster`, which `renderApp` has. */
export async function findToast(title: string) {
  const text = await screen.findByText(title, { selector: '[data-title]' })
  return text.closest('[data-sonner-toast]') as HTMLElement
}

/** Waits for the toast with [title] and checks its type, i.e. its icon. */
export async function expectToast(type: 'success' | 'warning' | 'error', title: string) {
  const toast = await findToast(title)
  expect(toast).toHaveAttribute('data-type', type)
  return toast
}
