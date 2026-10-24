import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
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
  const [unavailable, setUnavailable] = useState(false)
  const [sessionExpired, setSessionExpired] = useState(false)
  const loggedIn = useRef(false)
  useEffect(() => {
    loggedIn.current = Boolean(user)
  }, [user])

  const refresh = useCallback(
    () =>
      currentUser().then(
        (current) => {
          setUser(current)
          setUnavailable(false)
        },
        // Without an answer, a user who was logged in stays logged in; only a 401 ends the session.
        () => setUnavailable(true),
      ),
    [],
  )

  const logout = useCallback(async () => {
    await logoutRequest()
    setSessionExpired(false)
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      // A visitor who was never logged in gets a 401 as well, but no session ran out for them.
      if (loggedIn.current) setSessionExpired(true)
      setUser(null)
    })
    refresh()
    // The session expires after 8 hours without refresh; checking again when the tab comes back to the
    // foreground sends a user with an expired session to the login page.
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [refresh])

  const state = useMemo<AuthState>(
    () => ({ user, unavailable, sessionExpired, refresh, logout }),
    [user, unavailable, sessionExpired, refresh, logout],
  )
  return <AuthContext value={state}>{children}</AuthContext>
}
