import { Navigate, Outlet } from 'react-router'
import { useAuth } from './context'

/** Renders the nested routes only with a valid session, otherwise sends the user to the login page. */
export function RequireAuth() {
  const { user } = useAuth()
  if (user === undefined) return null
  if (user === null) return <Navigate to="/login" replace />
  return <Outlet />
}

/** The admin area is hidden from users; the backend rejects their requests to it anyway. */
export function RequireAdmin() {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}
