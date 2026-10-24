import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '@/auth/context'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** Frame of all pages behind the login: navigation, current user and logout. */
export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [logoutFailed, setLogoutFailed] = useState(false)
  if (!user) return null

  async function onLogout() {
    setLogoutFailed(false)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      setLogoutFailed(true)
    }
  }

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn('text-sm text-muted-foreground hover:text-foreground', isActive && 'text-foreground font-medium')

  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex max-w-4xl items-center gap-6 px-4 py-3">
          <span className="font-heading font-semibold">BITS</span>
          <nav className="flex gap-4">
            <NavLink to="/" end className={linkClass}>
              Start
            </NavLink>
            {user.role === 'ADMIN' && (
              <NavLink to="/admin" className={linkClass}>
                Verwaltung
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <Avatar size="sm">
              {user.avatarUrl && <AvatarImage src={user.avatarUrl} alt="" />}
              <AvatarFallback>{user.name.slice(0, 1).toUpperCase()}</AvatarFallback>
            </Avatar>
            <span className="text-sm">{user.name}</span>
            {logoutFailed && (
              <span role="alert" className="text-sm text-destructive">
                Abmelden fehlgeschlagen, versuche es erneut.
              </span>
            )}
            <Button variant="ghost" size="sm" onClick={onLogout}>
              Abmelden
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-4xl p-4">
        <Outlet />
      </main>
    </div>
  )
}
