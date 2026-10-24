import { useState, type ReactNode } from 'react'
import { ChevronsUpDownIcon, HouseIcon, LogOutIcon, SettingsIcon } from 'lucide-react'
import { Link, Outlet, useMatch, useNavigate } from 'react-router'
import { useAuth } from '@/auth/context'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
  useSidebar,
} from '@/components/ui/sidebar'
import type { Me } from '@/lib/api'
import { setTheme, storedTheme, type Theme } from '@/lib/theme'

/** Frame of all pages behind the login: navigation in the sidebar, current user and logout in its menu. */
export function Layout() {
  const { user } = useAuth()
  if (!user) return null

  return (
    // On the desktop the sidebar is always open; the open state only applies there, so changes are ignored.
    <SidebarProvider open onOpenChange={() => {}}>
      {/* The sidebar of shadcn is a plain div; the landmarks let screen readers jump to it. */}
      <aside aria-label="Seitenleiste">
        <Sidebar>
          <SidebarHeader>
            <span className="px-2 py-1.5 font-heading font-semibold">BITS</span>
          </SidebarHeader>
          <SidebarContent>
            <nav aria-label="Hauptnavigation">
              <SidebarGroup>
                <SidebarGroupContent>
                  <SidebarMenu>
                    <NavItem to="/" end icon={<HouseIcon />}>
                      Start
                    </NavItem>
                    {user.role === 'ADMIN' && (
                      <NavItem to="/admin" icon={<SettingsIcon />}>
                        Verwaltung
                      </NavItem>
                    )}
                  </SidebarMenu>
                </SidebarGroupContent>
              </SidebarGroup>
            </nav>
          </SidebarContent>
          <SidebarFooter className="p-2">
            <UserMenu user={user} />
          </SidebarFooter>
        </Sidebar>
      </aside>
      <SidebarInset>
        <header className="flex items-center gap-2 border-b px-4 py-2 md:hidden">
          <SidebarTrigger />
          <span className="font-heading font-semibold">BITS</span>
        </header>
        <div className="mx-auto w-full max-w-4xl p-4">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

function NavItem({ to, end = false, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: string }) {
  const active = useMatch({ path: to, end }) !== null
  const { setOpenMobile } = useSidebar()

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        isActive={active}
        render={<Link to={to} aria-current={active ? 'page' : undefined} onClick={() => setOpenMobile(false)} />}
      >
        {icon}
        <span>{children}</span>
      </SidebarMenuButton>
    </SidebarMenuItem>
  )
}

const THEMES: { value: Theme; label: string }[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Hell' },
  { value: 'dark', label: 'Dunkel' },
]

function UserMenu({ user }: { user: Me }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [theme, setThemeState] = useState(storedTheme)
  const [logoutFailed, setLogoutFailed] = useState(false)

  function onThemeChange(value: Theme) {
    setTheme(value)
    setThemeState(value)
  }

  async function onLogout() {
    setLogoutFailed(false)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      setLogoutFailed(true)
    }
  }

  return (
    <SidebarMenu>
      {logoutFailed && (
        <p role="alert" className="px-2 text-sm text-destructive">
          Abmelden fehlgeschlagen, versuche es erneut.
        </p>
      )}
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger render={<SidebarMenuButton size="lg" />}>
            <Avatar size="sm">
              {user.avatarUrl && <AvatarImage src={user.avatarUrl} alt="" />}
              <AvatarFallback>{user.name.slice(0, 1).toUpperCase()}</AvatarFallback>
            </Avatar>
            <span className="flex-1 truncate font-medium">{user.name}</span>
            <ChevronsUpDownIcon className="ml-auto" />
          </DropdownMenuTrigger>
          <DropdownMenuContent side="top" className="min-w-48">
            <DropdownMenuGroup>
              <DropdownMenuLabel>Theme</DropdownMenuLabel>
              <DropdownMenuRadioGroup value={theme} onValueChange={(value: Theme) => onThemeChange(value)}>
                {THEMES.map(({ value, label }) => (
                  <DropdownMenuRadioItem key={value} value={value}>
                    {label}
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuRadioGroup>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onLogout}>
              <LogOutIcon />
              Abmelden
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
