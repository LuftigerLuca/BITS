import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { toast } from 'sonner'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // A new Toaster shows the toasts still open, e.g. errors, which stay until closed.
  toast.dismiss()
})

// jsdom has no matchMedia; the dashboard follows the system theme with it.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }),
})

// jsdom has no pointer capture; the toasts use it to be swiped away.
Element.prototype.setPointerCapture = () => {}
Element.prototype.releasePointerCapture = () => {}
