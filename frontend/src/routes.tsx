import { Navigate, type RouteObject } from 'react-router'
import { RequireAdmin, RequireAuth } from './auth/guards.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { Layout } from './pages/Layout.tsx'
import { LoginPage } from './pages/LoginPage.tsx'

/** All pages of the dashboard; main.tsx serves them in the browser, the tests from memory. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <HomePage /> },
          {
            element: <RequireAdmin />,
            children: [
              {
                path: 'admin',
                // Only admins need the admin area, so its code is loaded on demand.
                lazy: async () => ({ Component: (await import('./pages/AdminPage.tsx')).AdminPage }),
              },
              {
                path: 'admin/audit-log',
                lazy: async () => ({ Component: (await import('./pages/AuditLogPage.tsx')).AuditLogPage }),
              },
              {
                path: 'admin/system-log',
                lazy: async () => ({ Component: (await import('./pages/SystemLogPage.tsx')).SystemLogPage }),
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
