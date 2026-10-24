import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Navigate, RouterProvider, createBrowserRouter } from 'react-router'
import './index.css'
import { AuthProvider } from './auth/AuthProvider.tsx'
import { RequireAdmin, RequireAuth } from './auth/guards.tsx'
import { AdminPage } from './pages/AdminPage.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { Layout } from './pages/Layout.tsx'
import { LoginPage } from './pages/LoginPage.tsx'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <HomePage /> },
          { element: <RequireAdmin />, children: [{ path: 'admin', element: <AdminPage /> }] },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
