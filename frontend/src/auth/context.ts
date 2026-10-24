import { createContext, useContext } from 'react'
import type { Me } from '@/lib/api'

export interface AuthState {
  /** `undefined` while the session is being checked, `null` without a valid session. */
  user: Me | null | undefined
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}
