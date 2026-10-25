import { StrictMode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createBrowserRouter } from 'react-router'
import './index.css'
import { AuthProvider } from './auth/AuthProvider.tsx'
import { Toaster } from './components/ui/sonner.tsx'
import { createQueryClient } from './lib/queryClient.ts'
import { followTheme } from './lib/theme.ts'
import { routes } from './routes.tsx'

followTheme()

const queryClient = createQueryClient()
const router = createBrowserRouter(routes)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
        <Toaster />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
