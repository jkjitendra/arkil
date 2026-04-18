import { useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Plus, Key, ExternalLink, AlertCircle, Loader2, RotateCcw, Trash2 } from 'lucide-react'
import { useProjects, useDeletedProjects, useRestoreProject } from '@/hooks/useProjects'
import { CreateProjectModal } from '@/components/CreateProjectModal'

export function ProjectsPage() {
  const navigate = useNavigate()
  const { data: projects, isLoading, error } = useProjects()
  const { data: deletedProjects } = useDeletedProjects()
  const restoreMutation = useRestoreProject()
  const [createModalOpen, setCreateModalOpen] = useState(false)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h2 className="text-xl font-semibold">Failed to load projects</h2>
        <p className="text-muted-foreground mt-2">{error.message}</p>
      </div>
    )
  }

  const projectList = projects || []

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Projects</h1>
          <p className="text-muted-foreground mt-1">
            Manage your Arkil authentication projects
          </p>
        </div>
        <Button onClick={() => setCreateModalOpen(true)}>
          <Plus className="h-4 w-4" />
          New Project
        </Button>
      </div>

      {/* Projects Grid */}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {projectList.map((project) => (
          <Link key={project.id} to="/projects/$projectId" params={{ projectId: project.id }}>
              <Card className="hover:border-primary/50 transition-colors cursor-pointer group h-full">
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border bg-muted/40 overflow-hidden">
                      {project.iconUrl ? (
                        <img src={project.iconUrl} alt={`${project.name} icon`} className="h-full w-full object-cover" />
                      ) : (
                        <span className="text-sm font-semibold">{project.name.charAt(0).toUpperCase()}</span>
                      )}
                    </div>
                    <div>
                    <CardTitle className="group-hover:text-primary transition-colors">
                      {project.name}
                    </CardTitle>
                    <CardDescription className="font-mono text-xs mt-1">
                      {project.slug}
                    </CardDescription>
                      {project.description && (
                        <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">
                          {project.description}
                        </p>
                      )}
                    </div>
                  </div>
                  <span
                    className={`px-2 py-1 rounded text-xs font-medium ${project.environment === 'PRODUCTION'
                      ? 'bg-success/20 text-success'
                      : 'bg-warning/20 text-warning'
                      }`}
                  >
                    {project.environment}
                  </span>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <Key className="h-4 w-4" />
                    <span>{project.allowedOrigins?.length || 0} origins</span>
                  </div>
                  <ExternalLink className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      {/* Recently Deleted */}
      {deletedProjects && deletedProjects.length > 0 && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <Trash2 className="h-5 w-5 text-muted-foreground" />
            <h2 className="text-lg font-semibold">Recently Deleted</h2>
            <span className="text-sm text-muted-foreground">
              ({deletedProjects.length} project{deletedProjects.length > 1 ? 's' : ''})
            </span>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {deletedProjects.map((project) => (
              <Card key={project.id} className="border-dashed opacity-60 hover:opacity-100 transition-opacity">
                <CardHeader className="pb-2">
                  <div className="flex items-start justify-between">
                    <div>
                      <CardTitle className="text-base line-through">{project.name}</CardTitle>
                      <CardDescription className="font-mono text-xs">
                        {project.slug}
                      </CardDescription>
                    </div>
                    <span className="text-xs text-warning bg-warning/10 px-2 py-1 rounded">
                      {project.daysRemaining} day{project.daysRemaining !== 1 ? 's' : ''} left
                    </span>
                  </div>
                </CardHeader>
                <CardContent>
                  <Button
                    variant="outline"
                    size="sm"
                    className="w-full"
                    disabled={restoreMutation.isPending}
                    onClick={async () => {
                      try {
                        const restored = await restoreMutation.mutateAsync(project.id)
                        // Navigate to the restored project
                        navigate({ to: '/projects/$projectId', params: { projectId: restored.id } })
                      } catch (error) {
                        console.error('Restore failed:', error)
                      }
                    }}
                  >
                    {restoreMutation.isPending ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <RotateCcw className="h-4 w-4" />
                    )}
                    Restore Project
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Quick Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Total Projects</CardDescription>
            <CardTitle className="text-4xl">{projectList.length}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Active API Keys</CardDescription>
            <CardTitle className="text-4xl">—</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>This Month's Requests</CardDescription>
            <CardTitle className="text-4xl">—</CardTitle>
          </CardHeader>
        </Card>
      </div>

      {/* Create Project Modal */}
      <CreateProjectModal open={createModalOpen} onOpenChange={setCreateModalOpen} />
    </div>
  )
}
