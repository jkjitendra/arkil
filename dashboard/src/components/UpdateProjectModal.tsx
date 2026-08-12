import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2, X, Plus } from 'lucide-react'
import { useUpdateProject } from '@/hooks/useProjects'
import type { Project } from '@/lib/api'
import { toast } from 'sonner'

interface UpdateProjectModalProps {
  project: Project
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function UpdateProjectModal({ project, open, onOpenChange }: UpdateProjectModalProps) {
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description || '')
  const [iconUrl, setIconUrl] = useState(project.iconUrl || '')
  const [environment, setEnvironment] = useState<'DEVELOPMENT' | 'PRODUCTION'>(project.environment)
  const [allowedOrigins, setAllowedOrigins] = useState<string[]>(project.allowedOrigins || [])
  const [redirectUris, setRedirectUris] = useState<string[]>(project.redirectUris || [])
  const [newOrigin, setNewOrigin] = useState('')
  const [newRedirect, setNewRedirect] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const updateMutation = useUpdateProject(project.id)

  // Reset form when project changes
  useEffect(() => {
    setName(project.name)
    setDescription(project.description || '')
    setIconUrl(project.iconUrl || '')
    setEnvironment(project.environment)
    setAllowedOrigins(project.allowedOrigins || [])
    setRedirectUris(project.redirectUris || [])
  }, [project])

  // Validate origin: https required (except localhost), no path/query/hash
  const validateOrigin = (origin: string): string | null => {
    try {
      const url = new URL(origin)
      const isLocal = ['localhost', '127.0.0.1'].includes(url.hostname)

      // Require https for non-localhost
      if (!isLocal && url.protocol !== 'https:') {
        return 'Non-localhost origins must use https://'
      }

      // Disallow path (except /), query, hash
      if ((url.pathname !== '/' && url.pathname !== '') || url.search || url.hash) {
        return 'Origins should not include path, query, or hash'
      }

      return null
    } catch {
      return 'Invalid URL format'
    }
  }

  // Validate redirect URI: https required (except localhost), no hash allowed
  const validateRedirectUri = (uri: string): string | null => {
    try {
      const url = new URL(uri)
      const isLocal = ['localhost', '127.0.0.1'].includes(url.hostname)

      // Require https for non-localhost
      if (!isLocal && url.protocol !== 'https:') {
        return 'Non-localhost URIs must use https://'
      }

      // Disallow hash fragment
      if (url.hash) {
        return 'Redirect URIs should not include hash fragments'
      }

      return null
    } catch {
      return 'Invalid URL format'
    }
  }

  const addOrigin = () => {
    const origin = newOrigin.trim()
    if (!origin) return

    if (allowedOrigins.includes(origin)) {
      setErrors({ ...errors, origin: 'Origin already added' })
      return
    }

    const error = validateOrigin(origin)
    if (error) {
      setErrors({ ...errors, origin: error })
      return
    }

    // Normalize: remove trailing slash
    const normalizedOrigin = origin.replace(/\/$/, '')
    setAllowedOrigins([...allowedOrigins, normalizedOrigin])
    setNewOrigin('')
    setErrors({ ...errors, origin: '' })
  }

  const removeOrigin = (origin: string) => {
    setAllowedOrigins(allowedOrigins.filter((o) => o !== origin))
  }

  const addRedirect = () => {
    const uri = newRedirect.trim()
    if (!uri) return

    if (redirectUris.includes(uri)) {
      setErrors({ ...errors, redirect: 'Redirect URI already added' })
      return
    }

    const error = validateRedirectUri(uri)
    if (error) {
      setErrors({ ...errors, redirect: error })
      return
    }

    setRedirectUris([...redirectUris, uri])
    setNewRedirect('')
    setErrors({ ...errors, redirect: '' })
  }

  const removeRedirect = (uri: string) => {
    setRedirectUris(redirectUris.filter((u) => u !== uri))
  }

  const validate = () => {
    const newErrors: Record<string, string> = {}
    if (!name.trim()) newErrors.name = 'Name is required'
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    try {
      await updateMutation.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        iconUrl: iconUrl.trim() || undefined,
        environment,
        allowedOrigins,
        redirectUris,
      })
      onOpenChange(false)
      toast.success('Project updated')
    } catch (error) {
      toast.error('Failed to update project')
      setErrors({ submit: error instanceof Error ? error.message : 'Failed to update project' })
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Edit Project</DialogTitle>
          <DialogDescription>
            Update your project settings and security configuration.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="min-w-0 space-y-4">
          <div>
            <label className="text-sm font-medium">Project Name</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My App"
              className="mt-1.5"
            />
            {errors.name && <p className="text-sm text-destructive mt-1">{errors.name}</p>}
          </div>

          <div>
            <label className="text-sm font-medium">Slug</label>
            <Input
              value={project.slug}
              disabled
              className="mt-1.5 font-mono bg-muted"
            />
            <p className="text-xs text-muted-foreground mt-1">Slug cannot be changed</p>
          </div>

          <div>
            <label className="text-sm font-medium">Description</label>
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description"
              className="mt-1.5"
            />
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="text-sm font-medium">Icon URL</label>
              <Input
                value={iconUrl}
                onChange={(e) => setIconUrl(e.target.value)}
                placeholder="https://cdn.example.com/icon.png"
                className="mt-1.5"
              />
              <p className="mt-1 text-xs text-muted-foreground">
                Displayed in the dashboard and hosted project metadata.
              </p>
            </div>
            <div>
              <label className="text-sm font-medium">Environment</label>
              <Select
                value={environment}
                onValueChange={(value) => setEnvironment(value as 'DEVELOPMENT' | 'PRODUCTION')}
              >
                <SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="DEVELOPMENT">Development</SelectItem>
                  <SelectItem value="PRODUCTION">Production</SelectItem>
                </SelectContent>
              </Select>
              <p className="mt-1 text-xs text-muted-foreground">
                Production mode enforces stricter origin and redirect validation.
              </p>
            </div>
          </div>

          {/* Allowed Origins */}
          <div>
            <label className="text-sm font-medium">Allowed Origins</label>
            <p className="text-xs text-muted-foreground mb-2">
              Domains allowed to make authenticated requests
            </p>
            <div className="space-y-2">
              {allowedOrigins.map((origin) => (
                <div key={origin} className="flex min-w-0 items-center gap-2">
                  <code className="min-w-0 flex-1 truncate rounded bg-muted px-2 py-1 font-mono text-sm">
                    {origin}
                  </code>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7"
                    onClick={() => removeOrigin(origin)}
                  >
                    <X className="h-3 w-3" />
                  </Button>
                </div>
              ))}
              <div className="flex min-w-0 gap-2">
                <Input
                  value={newOrigin}
                  onChange={(e) => setNewOrigin(e.target.value)}
                  placeholder="https://example.com"
                  className="min-w-0 flex-1 font-mono text-sm"
                  onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addOrigin())}
                />
                <Button type="button" variant="outline" size="sm" onClick={addOrigin}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
              {errors.origin && <p className="text-sm text-destructive">{errors.origin}</p>}
            </div>
          </div>

          {/* Redirect URIs */}
          <div>
            <label className="text-sm font-medium">Redirect URIs</label>
            <p className="text-xs text-muted-foreground mb-2">
              Allowed callback URLs for OAuth flows
            </p>
            <div className="space-y-2">
              {redirectUris.map((uri) => (
                <div key={uri} className="flex min-w-0 items-center gap-2">
                  <code className="min-w-0 flex-1 truncate rounded bg-muted px-2 py-1 font-mono text-sm">
                    {uri}
                  </code>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7"
                    onClick={() => removeRedirect(uri)}
                  >
                    <X className="h-3 w-3" />
                  </Button>
                </div>
              ))}
              <div className="flex min-w-0 gap-2">
                <Input
                  value={newRedirect}
                  onChange={(e) => setNewRedirect(e.target.value)}
                  placeholder="https://example.com/callback"
                  className="min-w-0 flex-1 font-mono text-sm"
                  onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addRedirect())}
                />
                <Button type="button" variant="outline" size="sm" onClick={addRedirect}>
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
              {errors.redirect && <p className="text-sm text-destructive">{errors.redirect}</p>}
            </div>
          </div>

          {errors.submit && (
            <p className="text-sm text-destructive bg-destructive/10 px-3 py-2 rounded">
              {errors.submit}
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Save Changes
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
