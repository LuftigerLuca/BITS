import { toast } from 'sonner'

/**
 * The result of an action as a toast; the icons come from the `Toaster`, so features only pick the type. Success and
 * warning close after a few seconds, an error stays until it is closed, so it is not missed.
 */
export const notify = {
  success: (message: string, description?: string) => toast.success(message, { description, duration: 4000 }),
  warning: (message: string, description?: string) => toast.warning(message, { description, duration: 4000 }),
  error: (message: string, description?: string) => toast.error(message, { description, duration: Infinity }),
}
