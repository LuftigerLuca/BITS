import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, getMe, logout as logoutRequest, setUnauthorizedHandler, type Me } from '@/lib/api'
import { AuthContext, type AuthState } from './context'

/** The user of the current session, or `null` without one. */
async function currentUser(): Promise<Me | null> {
  try {
    return await getMe()
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null | undefined>(undefined)

  const refresh = useCallback(async () => setUser(await currentUser()), [])

  const logout = useCallback(async () => {
    await logoutRequest()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    currentUser().then(setUser)
    // The session expires after 8 hours without refresh; checking again when the tab comes back to the
    // foreground sends a user with an expired session to the login page.
    const onVisible = () => {
      if (document.visibilityState === 'visible') currentUser().then(setUser)
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [])

  const state = useMemo<AuthState>(() => ({ user, refresh, logout }), [user, refresh, logout])
  return <AuthContext value={state}>{children}</AuthContext>
}
