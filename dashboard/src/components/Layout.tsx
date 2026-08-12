import { Link, useLocation } from '@tanstack/react-router'
import { useEffect, useState, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import {
  ChevronRight,
  CircleUserRound,
  Hexagon,
  KeyRound,
  LogOut,
  Monitor,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  Sun,
  Users,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuth } from '@/lib/auth'
import { useProfile } from '@/hooks/useProfile'
import { useTheme } from '@/theme/ThemeContext'
import { Avatar, AvatarFallback } from './ui/avatar'
import { Button } from './ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from './ui/dropdown-menu'
import { Sheet, SheetContent } from './ui/sheet'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './ui/tooltip'
import { TopBar } from './TopBar'

const SIDEBAR_STORAGE_KEY = 'arkil-sidebar-collapsed'

type NavigationItem = {
  name: string
  href: '/' | '/keys' | '/users' | '/settings'
  icon: LucideIcon
  active: boolean
}

function getStoredSidebarPreference() {
  return localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true'
}

function initials(value: string) {
  return value
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('') || 'A'
}

type SidebarContentProps = {
  collapsed: boolean
  navigation: NavigationItem[]
  userName: string
  userEmail: string
  tenantName?: string
  onCollapseToggle?: () => void
  onNavigate?: () => void
  onSignOut: () => void
}

function SidebarContent({
  collapsed,
  navigation,
  userName,
  userEmail,
  tenantName,
  onCollapseToggle,
  onNavigate,
  onSignOut,
}: SidebarContentProps) {
  const { theme, setTheme } = useTheme()

  const renderNavItem = (item: NavigationItem) => {
    const content = (
      <Link
        key={item.name}
        to={item.href}
        onClick={onNavigate}
        aria-current={item.active ? 'page' : undefined}
        className={cn(
          'flex h-10 items-center gap-3 border-l-[3px] px-3 text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background',
          collapsed ? 'justify-center px-0' : '',
          item.active
            ? 'border-primary bg-primary-subtle text-foreground'
            : 'border-transparent text-foreground-secondary hover:bg-surface-raised hover:text-foreground',
        )}
      >
        <item.icon className="size-[18px] shrink-0" aria-hidden="true" />
        {!collapsed ? <span className="min-w-0 truncate">{item.name}</span> : null}
      </Link>
    )

    if (!collapsed) return content

    return (
      <Tooltip key={item.name}>
        <TooltipTrigger asChild>{content}</TooltipTrigger>
        <TooltipContent side="right">{item.name}</TooltipContent>
      </Tooltip>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-surface">
      <div className={cn('flex h-14 shrink-0 items-center border-b border-border', collapsed ? 'justify-center px-2' : 'justify-between px-4')}>
        <Link to="/" onClick={onNavigate} aria-label="Arkil home" className="flex min-w-0 items-center gap-2.5 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
          <img src="/arkil_logo_enhanced.png" alt="" className="size-9 shrink-0 object-contain" />
          {!collapsed ? <span className="text-base font-semibold tracking-tight text-foreground">Arkil</span> : null}
        </Link>
        {onCollapseToggle ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="size-8 text-foreground-muted" onClick={onCollapseToggle} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
                {collapsed ? <PanelLeftOpen className="size-4" /> : <PanelLeftClose className="size-4" />}
              </Button>
            </TooltipTrigger>
            <TooltipContent side="right">{collapsed ? 'Expand sidebar' : 'Collapse sidebar'}</TooltipContent>
          </Tooltip>
        ) : null}
      </div>

      <nav className="min-h-0 flex-1 overflow-y-auto py-4" aria-label="Primary navigation">
        {!collapsed ? <p className="mb-2 px-4 text-[11px] font-medium tracking-[0.08em] text-foreground-muted">WORKSPACE</p> : null}
        <div className="space-y-1">{navigation.map(renderNavItem)}</div>

        <div className="mt-7 border-t border-border pt-4">
          {!collapsed ? <p className="mb-2 px-4 text-[11px] font-medium tracking-[0.08em] text-foreground-muted">ORGANIZATION</p> : null}
          <div className={cn('flex h-9 items-center gap-3 text-sm text-foreground-secondary', collapsed ? 'justify-center px-0' : 'px-4')}>
            <Hexagon className="size-[17px] shrink-0 text-foreground-muted" strokeWidth={1.7} aria-hidden="true" />
            {!collapsed ? <span className="truncate">{tenantName || 'Workspace'}</span> : null}
          </div>
        </div>
      </nav>

      <div className="shrink-0 border-t border-border p-3">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              className={cn('flex w-full items-center gap-2.5 rounded-lg p-1.5 text-left transition-colors duration-150 hover:bg-surface-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background', collapsed && 'justify-center')}
              aria-label="Open account menu"
            >
              <Avatar className="size-8 border border-border">
                <AvatarFallback>{initials(userName)}</AvatarFallback>
              </Avatar>
              {!collapsed ? (
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-foreground">{userName}</span>
                  <span className="block truncate text-xs text-foreground-muted">{userEmail}</span>
                </span>
              ) : null}
              {!collapsed ? <ChevronRight className="size-4 shrink-0 text-foreground-muted" aria-hidden="true" /> : null}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent side={collapsed ? 'right' : 'top'} align="end" className="w-56">
            <DropdownMenuLabel>
              <span className="block truncate text-sm text-foreground">{userName}</span>
              <span className="block truncate pt-0.5 font-normal text-foreground-muted">{userEmail}</span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/settings" onClick={() => { window.dispatchEvent(new Event('arkil:open-settings-profile')); onNavigate?.() }}>
                <CircleUserRound className="size-4" />
                Profile
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSub>
              <DropdownMenuSubTrigger>
                <Monitor className="mr-2 size-4" />
                Appearance
              </DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                <DropdownMenuCheckboxItem checked={theme === 'light'} onCheckedChange={() => setTheme('light')}>
                  <Sun className="mr-2 size-4" />
                  Light
                </DropdownMenuCheckboxItem>
                <DropdownMenuCheckboxItem checked={theme === 'dark'} onCheckedChange={() => setTheme('dark')}>
                  <Moon className="mr-2 size-4" />
                  Dark
                </DropdownMenuCheckboxItem>
                <DropdownMenuCheckboxItem checked={theme === 'system'} onCheckedChange={() => setTheme('system')}>
                  <Monitor className="mr-2 size-4" />
                  System
                </DropdownMenuCheckboxItem>
              </DropdownMenuSubContent>
            </DropdownMenuSub>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="text-danger focus:text-danger" onSelect={onSignOut}>
              <LogOut className="size-4" />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}

export function Layout({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { user, logout } = useAuth()
  const { data: profile } = useProfile()
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(getStoredSidebarPreference)

  useEffect(() => {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarCollapsed))
  }, [sidebarCollapsed])

  const userEmail = profile?.email || user?.profile?.email || 'user@example.com'
  const userName = profile?.displayName || user?.profile?.name || user?.profile?.preferred_username || userEmail
  const tenantName = profile?.tenant.name
  const canManageUsers = !!profile?.roles.some((role) => ['TENANT_ADMIN', 'SUPER_ADMIN', 'PLATFORM_ADMIN'].includes(role))
  const navigation: NavigationItem[] = [
    { name: 'Projects', href: '/', icon: Hexagon, active: location.pathname === '/' || location.pathname.startsWith('/projects/') },
    { name: 'API Keys', href: '/keys', icon: KeyRound, active: location.pathname === '/keys' },
    ...(canManageUsers ? [{ name: 'Users', href: '/users' as const, icon: Users, active: location.pathname === '/users' }] : []),
    { name: 'Settings', href: '/settings', icon: Settings, active: location.pathname === '/settings' },
  ]

  const handleSignOut = () => {
    void logout().catch((error: unknown) => console.error('Logout error:', error))
  }

  return (
    <TooltipProvider delayDuration={200}>
      <div className="min-h-screen bg-background">
        <aside className={cn('fixed inset-y-0 left-0 z-40 hidden border-r border-border bg-surface lg:flex lg:flex-col', sidebarCollapsed ? 'lg:w-[72px]' : 'lg:w-[272px]')}>
          <SidebarContent
            collapsed={sidebarCollapsed}
            navigation={navigation}
            userName={userName}
            userEmail={userEmail}
            tenantName={tenantName}
            onCollapseToggle={() => setSidebarCollapsed((current) => !current)}
            onSignOut={handleSignOut}
          />
        </aside>

        <Sheet open={mobileNavigationOpen} onOpenChange={setMobileNavigationOpen}>
          <SheetContent side="left" className="!w-[272px] p-0 lg:hidden">
            <SidebarContent
              collapsed={false}
              navigation={navigation}
              userName={userName}
              userEmail={userEmail}
              tenantName={tenantName}
              onNavigate={() => setMobileNavigationOpen(false)}
              onSignOut={handleSignOut}
            />
          </SheetContent>
        </Sheet>

        <div className={cn('min-w-0 transition-[padding] duration-150 ease-out', sidebarCollapsed ? 'lg:pl-[72px]' : 'lg:pl-[272px]')}>
          <TopBar onOpenMobileNavigation={() => setMobileNavigationOpen(true)} />
          <main className="min-w-0 p-4 sm:p-6 lg:p-8">{children}</main>
        </div>
      </div>
    </TooltipProvider>
  )
}
