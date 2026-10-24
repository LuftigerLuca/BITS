import { createContext, useContext } from 'react'
import type { Me } from '@/lib/api'

export interface AuthState {
  /** `undefined` while the session is being checked or could not be checked, `null` without a valid session. */
  user: Me | null | undefined
  /** Whether the last check of the session got no answer, e.g. because the backend is being deployed. */
  unavailable: boolean
  /** Whether the session of a logged-in user ended with a 401, so the login page can say why it shows up. */
  sessionExpired: boolean
  /** Checks the session again; never rejects. */
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}
