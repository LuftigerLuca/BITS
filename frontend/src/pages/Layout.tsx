import { useEffect, useRef, useState, type ReactNode } from 'react'
import {
  ActivityIcon,
  BookOpenIcon,
  ChevronsUpDownIcon,
  CircleUserIcon,
  GaugeIcon,
  HistoryIcon,
  HouseIcon,
  LandmarkIcon,
  LogOutIcon,
  SettingsIcon,
  ShieldIcon,
  SwordsIcon,
  UsersIcon,
  VoteIcon,
} from 'lucide-react'
import { Link, Outlet, useLocation, useMatch, useNavigate } from 'react-router'
import { useAuth } from '@/auth/context'
import { useVoteAccess } from '@/features/votes/queries'
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
  SidebarGroupLabel,
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
  // Whether the user may manage votes depends on their Discord roles, which only the backend knows.
  const voteAccess = useVoteAccess()
  const mainRef = useFocusPageOnNavigation()
  if (!user) return null
  const isAdmin = user.role === 'ADMIN'
  const canManageVotes = voteAccess.data?.canManage === true

  return (
    // On the desktop the sidebar is always open; the open state only applies there, so changes are ignored.
    <SidebarProvider open onOpenChange={() => {}}>
      <a
        href="#main"
        className="sr-only z-50 rounded-md bg-background px-3 py-2 text-sm font-medium ring-2 ring-ring focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        Zum Inhalt springen
      </a>
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
                    {user.discordUser && (
                      <NavItem to="/account" icon={<CircleUserIcon />}>
                        Mein Account
                      </NavItem>
                    )}
                  </SidebarMenu>
                </SidebarGroupContent>
              </SidebarGroup>
              {/* Members of the roles that may manage votes see the group too, with the votes only. */}
              {(isAdmin || canManageVotes) && (
                <SidebarGroup>
                  <SidebarGroupLabel>Admin</SidebarGroupLabel>
                  <SidebarGroupContent>
                    <SidebarMenu>
                      {isAdmin && (
                        <>
                          <NavItem to="/admin/roles" icon={<ShieldIcon />}>
                            Rollen
                          </NavItem>
                          <NavItem to="/admin/members" icon={<UsersIcon />}>
                            Mitglieder
                          </NavItem>
                          <NavItem to="/admin/military-units" icon={<SwordsIcon />}>
                            Militäreinheiten
                          </NavItem>
                          <NavItem to="/admin/embassies" icon={<LandmarkIcon />}>
                            Botschaften
                          </NavItem>
                        </>
                      )}
                      {canManageVotes && (
                        <NavItem to="/votes" icon={<VoteIcon />}>
                          Abstimmungen
                        </NavItem>
                      )}
                    </SidebarMenu>
                  </SidebarGroupContent>
                </SidebarGroup>
              )}
              {/* Settings and logs of BITS itself, apart from the features they configure and record. */}
              {isAdmin && (
                <SidebarGroup>
                  <SidebarGroupLabel>System</SidebarGroupLabel>
                  <SidebarGroupContent>
                    <SidebarMenu>
                      {/* Only the page itself, so the pages below it in the path do not mark it as well. */}
                      <NavItem to="/admin" end icon={<SettingsIcon />}>
                        Verwaltung
                      </NavItem>
                      <NavItem to="/admin/audit-log" icon={<HistoryIcon />}>
                        Audit-Log
                      </NavItem>
                      <NavItem to="/admin/system-log" icon={<ActivityIcon />}>
                        System-Log
                      </NavItem>
                      <NavItem to="/admin/system-status" icon={<GaugeIcon />}>
                        System-Status
                      </NavItem>
                    </SidebarMenu>
                  </SidebarGroupContent>
                </SidebarGroup>
              )}
            </nav>
          </SidebarContent>
          <SidebarFooter className="p-2">
            <UserMenu user={user} />
          </SidebarFooter>
        </Sidebar>
      </aside>
      <SidebarInset id="main" ref={mainRef} tabIndex={-1} className="outline-none">
        <header className="flex items-center gap-2 border-b px-4 py-2 md:hidden">
          <SidebarTrigger />
          <span className="font-heading font-semibold">BITS</span>
        </header>
        <div className="mx-auto w-full max-w-6xl p-4">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

/**
 * After a navigation within the dashboard, moves the focus to the heading of the new page, so that screen readers
 * announce it and the next Tab starts there rather than in the sidebar. Not on the first load, which the browser
 * announces itself.
 */
function useFocusPageOnNavigation() {
  const { pathname } = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  const firstRender = useRef(true)
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    const main = mainRef.current
    const heading = main?.querySelector<HTMLElement>('h1[tabindex]')
    ;(heading ?? main)?.focus()
  }, [pathname])
  return mainRef
}

function NavItem({
  to,
  end = false,
  icon,
  children,
}: {
  to: string
  end?: boolean
  icon: ReactNode
  children: string
}) {
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
    <>
      {/* Outside of the menu, since a list may only hold list items. */}
      {logoutFailed && (
        <p role="alert" className="px-2 text-sm text-destructive">
          Abmelden fehlgeschlagen, versuche es erneut.
        </p>
      )}
      <SidebarMenu>
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
                <DropdownMenuLabel>Darstellung</DropdownMenuLabel>
                <DropdownMenuRadioGroup value={theme} onValueChange={(value: Theme) => onThemeChange(value)}>
                  {THEMES.map(({ value, label }) => (
                    <DropdownMenuRadioItem key={value} value={value}>
                      {label}
                    </DropdownMenuRadioItem>
                  ))}
                </DropdownMenuRadioGroup>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuItem render={<Link to="/docs" />}>
                <BookOpenIcon />
                Handbuch
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onLogout}>
                <LogOutIcon />
                Abmelden
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
    </>
  )
}
