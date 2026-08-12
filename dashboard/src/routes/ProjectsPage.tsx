import { useMemo, useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { AlertCircle, Clock3, FolderOpen, Globe2, Loader2, Plus, RotateCcw, Search, Trash2 } from 'lucide-react'
import { CreateProjectModal } from '@/components/CreateProjectModal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { EmptyState } from '@/components/ui/empty-state'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { useDeletedProjects, useProjects, useRestoreProject } from '@/hooks/useProjects'
import { toast } from 'sonner'

function formatUpdatedAt(updatedAt: string) {
  const date = new Date(updatedAt)
  if (Number.isNaN(date.getTime())) return 'Updated recently'

  return `Updated ${new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(date)}`
}

function EnvironmentBadge({ environment }: { environment: 'DEVELOPMENT' | 'PRODUCTION' }) {
  const isProduction = environment === 'PRODUCTION'

  return (
    <span className={isProduction ? 'inline-flex items-center gap-1.5 text-[11px] font-medium text-success' : 'inline-flex items-center gap-1.5 text-[11px] font-medium text-warning'}>
      <span className={isProduction ? 'size-1.5 rounded-full bg-success' : 'size-1.5 rounded-full bg-warning'} aria-hidden="true" />
      {isProduction ? 'Production' : 'Development'}
    </span>
  )
}

function ProjectSkeletons() {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: 6 }, (_, index) => (
        <Card key={index} className="p-4">
          <div className="flex gap-3">
            <Skeleton className="size-10 shrink-0" />
            <div className="min-w-0 flex-1 space-y-2">
              <Skeleton className="h-4 w-3/5" />
              <Skeleton className="h-3 w-2/5" />
            </div>
          </div>
          <div className="mt-5 space-y-2">
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-4/5" />
          </div>
          <div className="mt-5 flex justify-between border-t border-border pt-3">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-3 w-24" />
          </div>
        </Card>
      ))}
    </div>
  )
}

export function ProjectsPage() {
  const navigate = useNavigate()
  const { data: projects, isLoading, error } = useProjects()
  const { data: deletedProjects } = useDeletedProjects()
  const restoreMutation = useRestoreProject()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [search, setSearch] = useState('')

  const projectList = useMemo(() => projects || [], [projects])
  const filteredProjects = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return projectList
    return projectList.filter((project) => project.name.toLowerCase().includes(query) || project.slug.toLowerCase().includes(query))
  }, [projectList, search])

  const restoreProject = async (projectId: string) => {
    try {
      const restored = await restoreMutation.mutateAsync(projectId)
      toast.success('Project restored')
      navigate({ to: '/projects/$projectId', params: { projectId: restored.id } })
    } catch (restoreError) {
      toast.error('Failed to restore project')
      console.error('Restore failed:', restoreError)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        description="Create and manage authentication environments."
        actions={
          <Button onClick={() => setCreateModalOpen(true)}>
            <Plus className="size-4" />
            New project
          </Button>
        }
      />

      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-foreground-muted" aria-hidden="true" />
        <Input value={search} onChange={(event) => setSearch(event.target.value)} className="pl-9" placeholder="Search projects by name or slug…" aria-label="Search projects" />
      </div>

      {isLoading ? <ProjectSkeletons /> : null}

      {!isLoading && error ? (
        <div className="flex min-h-80 flex-col items-center justify-center rounded-lg border border-danger/30 bg-danger/5 px-6 text-center">
          <AlertCircle className="size-8 text-danger" aria-hidden="true" />
          <h2 className="mt-3 text-base font-semibold text-foreground">Failed to load projects</h2>
          <p className="mt-1 max-w-md text-sm text-foreground-secondary">{error.message}</p>
        </div>
      ) : null}

      {!isLoading && !error && filteredProjects.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filteredProjects.map((project) => (
            <Link
              key={project.id}
              to="/projects/$projectId"
              params={{ projectId: project.id }}
              className="group block h-full rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background"
            >
              <Card className="h-full p-4 transition-colors duration-150 hover:border-primary/30 hover:shadow-sm">
                <div className="flex items-start gap-3">
                  <div className="flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-surface-raised text-sm font-semibold text-foreground-secondary">
                    {project.iconUrl ? <img src={project.iconUrl} alt="" className="size-full object-cover" /> : project.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <h2 className="truncate text-sm font-semibold text-foreground transition-colors duration-150 group-hover:text-primary">{project.name}</h2>
                      <EnvironmentBadge environment={project.environment} />
                    </div>
                    <p className="mt-1 truncate font-mono text-xs text-foreground-muted">{project.slug}</p>
                  </div>
                </div>

                <p className="mt-4 line-clamp-2 min-h-10 text-sm leading-5 text-foreground-secondary">
                  {project.description || 'No project description provided.'}
                </p>

                <div className="mt-4 flex items-center justify-between gap-3 border-t border-border pt-3 text-xs text-foreground-muted">
                  <span className="inline-flex items-center gap-1.5"><Globe2 className="size-3.5" aria-hidden="true" />{project.allowedOrigins?.length || 0} origins</span>
                  <span className="truncate">{formatUpdatedAt(project.updatedAt)}</span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      ) : null}

      {!isLoading && !error && filteredProjects.length === 0 ? (
        <EmptyState
          icon={FolderOpen}
          title={search ? 'No matching projects' : 'No projects yet'}
          description={search ? 'Try a different project name or slug.' : 'Create a project to configure authentication for an application.'}
          action={!search ? <Button onClick={() => setCreateModalOpen(true)}><Plus className="size-4" />New project</Button> : undefined}
        />
      ) : null}

      {deletedProjects && deletedProjects.length > 0 ? (
        <section className="border-t border-border pt-6" aria-labelledby="recently-deleted-heading">
          <div className="mb-3 flex items-center gap-2">
            <Trash2 className="size-4 text-foreground-muted" aria-hidden="true" />
            <h2 id="recently-deleted-heading" className="text-sm font-semibold text-foreground">Recently deleted</h2>
            <span className="text-xs text-foreground-muted">{deletedProjects.length}</span>
          </div>
          <div className="overflow-hidden rounded-lg border border-border bg-surface">
            {deletedProjects.map((project, index) => (
              <div key={project.id} className={index > 0 ? 'flex flex-col gap-3 border-t border-border px-4 py-3 sm:flex-row sm:items-center' : 'flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center'}>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-foreground">{project.name}</p>
                  <p className="mt-0.5 truncate font-mono text-xs text-foreground-muted">{project.slug}</p>
                </div>
                <Badge variant="outline" className="w-fit gap-1.5 text-warning"><Clock3 className="size-3" aria-hidden="true" />{project.daysRemaining} day{project.daysRemaining === 1 ? '' : 's'} remaining</Badge>
                <Button variant="outline" size="sm" disabled={restoreMutation.isPending} onClick={() => void restoreProject(project.id)}>
                  {restoreMutation.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <RotateCcw className="size-3.5" />}
                  Restore
                </Button>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <CreateProjectModal open={createModalOpen} onOpenChange={setCreateModalOpen} />
    </div>
  )
}
