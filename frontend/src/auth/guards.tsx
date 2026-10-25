import { Navigate, Outlet } from 'react-router'
import { UnavailablePage } from '@/pages/UnavailablePage'
import { useAuth } from './context'

/** Renders the nested routes only with a valid session, otherwise sends the user to the login page. */
export function RequireAuth() {
  const { user, unavailable } = useAuth()
  if (user === undefined) return unavailable ? <UnavailablePage /> : null
  if (user === null) return <Navigate to="/login" replace />
  return <Outlet />
}

/** The admin area is hidden from users; the backend rejects their requests to it anyway. */
export function RequireAdmin() {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}

/** Pages about the member's own Discord account; the admin account from the environment has none. */
export function RequireDiscordUser() {
  const { user } = useAuth()
  if (!user?.discordUser) return <Navigate to="/" replace />
  return <Outlet />
}
