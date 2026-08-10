import { Link, useLocation } from '@tanstack/react-router'
import { Menu } from 'lucide-react'
import { useProject } from '@/hooks/useProjects'
import { Breadcrumb, BreadcrumbItem, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from './ui/breadcrumb'
import { Button } from './ui/button'

type TopBarProps = {
  onOpenMobileNavigation: () => void
}

export function TopBar({ onOpenMobileNavigation }: TopBarProps) {
  const location = useLocation()
  const projectMatch = location.pathname.match(/^\/projects\/([^/]+)$/)
  const projectId = projectMatch?.[1] || ''
  const { data: project } = useProject(projectId)

  const isProjectDetail = Boolean(projectMatch)
  const currentPage = isProjectDetail
    ? project?.name || 'Project'
    : location.pathname === '/keys'
      ? 'API Keys'
      : location.pathname === '/users'
        ? 'Users'
        : location.pathname === '/settings'
          ? 'Settings'
          : 'Projects'

  return (
    <header className="sticky top-0 z-30 h-14 border-b border-border bg-surface/80 backdrop-blur">
      <div className="flex h-full items-center gap-3 px-4 sm:px-6 lg:px-8">
        <Button variant="ghost" size="icon" className="lg:hidden" onClick={onOpenMobileNavigation} aria-label="Open navigation">
          <Menu className="size-5" />
        </Button>
        <Breadcrumb className="min-w-0 flex-1">
          <BreadcrumbList className="flex-nowrap overflow-hidden whitespace-nowrap">
            {isProjectDetail ? (
              <>
                <BreadcrumbItem className="shrink-0">
                  <Link to="/" className="transition-colors duration-150 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
                    Projects
                  </Link>
                </BreadcrumbItem>
                <BreadcrumbSeparator className="shrink-0" />
              </>
            ) : null}
            <BreadcrumbItem className="min-w-0">
              <BreadcrumbPage className="block truncate">{currentPage}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
      </div>
    </header>
  )
}
