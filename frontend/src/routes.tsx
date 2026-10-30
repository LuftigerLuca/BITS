import { Navigate, type RouteObject } from 'react-router'
import { RequireAdmin, RequireAuth, RequireDiscordUser } from './auth/guards.tsx'
import { RequireVoteAccess } from './features/votes/RequireVoteAccess.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { Layout } from './pages/Layout.tsx'
import { LoginPage } from './pages/LoginPage.tsx'

/** All pages of the dashboard; main.tsx serves them in the browser, the tests from memory. */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  // The manual needs no login, so members who only use Discord can read it too.
  { path: '/docs/:slug?', lazy: async () => ({ Component: (await import('./pages/ManualPage.tsx')).ManualPage }) },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <HomePage /> },
          {
            element: <RequireVoteAccess />,
            children: [
              {
                path: 'votes',
                lazy: async () => ({ Component: (await import('./pages/VotesPage.tsx')).VotesPage }),
              },
            ],
          },
          {
            element: <RequireDiscordUser />,
            children: [
              {
                path: 'account',
                lazy: async () => ({ Component: (await import('./pages/AccountPage.tsx')).AccountPage }),
              },
            ],
          },
          {
            element: <RequireAdmin />,
            children: [
              {
                path: 'admin',
                // Only admins need the admin area, so its code is loaded on demand.
                lazy: async () => ({ Component: (await import('./pages/AdminPage.tsx')).AdminPage }),
              },
              {
                path: 'admin/roles',
                lazy: async () => ({ Component: (await import('./pages/RolesPage.tsx')).RolesPage }),
              },
              {
                path: 'admin/members',
                lazy: async () => ({ Component: (await import('./pages/MembersPage.tsx')).MembersPage }),
              },
              {
                path: 'admin/military-units',
                lazy: async () => ({ Component: (await import('./pages/MilitaryUnitsPage.tsx')).MilitaryUnitsPage }),
              },
              {
                path: 'admin/embassies',
                lazy: async () => ({ Component: (await import('./pages/EmbassiesPage.tsx')).EmbassiesPage }),
              },
              {
                path: 'admin/audit-log',
                lazy: async () => ({ Component: (await import('./pages/AuditLogPage.tsx')).AuditLogPage }),
              },
              {
                path: 'admin/system-log',
                lazy: async () => ({ Component: (await import('./pages/SystemLogPage.tsx')).SystemLogPage }),
              },
              {
                path: 'admin/system-status',
                lazy: async () => ({ Component: (await import('./pages/SystemStatusPage.tsx')).SystemStatusPage }),
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]
