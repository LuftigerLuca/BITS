import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { AuthProvider } from '@/auth/AuthProvider'
import { createQueryClient } from '@/lib/queryClient'
import { routes } from '@/routes'

/** The whole dashboard at [path], as the browser would show it; stub fetch first. */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = createQueryClient()
  // Errors show up right away instead of after the retry.
  queryClient.setDefaultOptions({ queries: { retry: false } })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}
