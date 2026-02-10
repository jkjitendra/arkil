import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2 } from 'lucide-react'
import { useCreateProject } from '@/hooks/useProjects'
import { ApiKeyDisplayModal } from './ApiKeyDisplayModal'
import { ApiError } from '@/lib/api'

interface CreateProjectModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function CreateProjectModal({ open, onOpenChange }: CreateProjectModalProps) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  // State for API key display modal
  const [keyModalOpen, setKeyModalOpen] = useState(false)
  const [createdKey, setCreatedKey] = useState<{ name: string; secretKey: string; publishableKey?: string; projectId: string } | null>(null)

  const createMutation = useCreateProject()

  const generateSlug = (name: string) => {
    return name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '')
  }

  const handleNameChange = (value: string) => {
    setName(value)
    if (!slug || slug === generateSlug(name)) {
      setSlug(generateSlug(value))
    }
  }

  const validate = () => {
    const newErrors: Record<string, string> = {}
    if (!name.trim()) newErrors.name = 'Name is required'
    if (!slug.trim()) newErrors.slug = 'Slug is required'
    if (slug && !/^[a-z0-9-]+$/.test(slug)) {
      newErrors.slug = 'Slug must be lowercase letters, numbers, and hyphens only'
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    try {
      const result = await createMutation.mutateAsync({
        name: name.trim(),
        slug: slug.trim(),
        description: description.trim() || undefined,
        environment: 'DEVELOPMENT',
      })

      // Reset form and close create modal
      setName('')
      setSlug('')
      setDescription('')
      setErrors({})
      onOpenChange(false)

      // If a secret key was returned, show the key modal BEFORE navigating
      if (result.secretKey) {
        setCreatedKey({
          name: result.project.name,
          secretKey: result.secretKey,
          projectId: result.project.id,
        })
        setKeyModalOpen(true)
        // Navigation will happen when key modal closes
      } else {
        // No key to display, navigate immediately
        navigate({ to: '/projects/$projectId', params: { projectId: result.project.id } })
      }
    } catch (error) {
      if (error instanceof ApiError) {
        const validationErrors = error.getValidationErrors()
        if (validationErrors.length > 0) {
          setErrors({ submit: validationErrors.join(', ') })
        } else {
          setErrors({ submit: error.message })
        }
      } else {
        setErrors({ submit: error instanceof Error ? error.message : 'Failed to create project' })
      }
    }
  }

  // Fix: Accept boolean and only reset when closing
  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      setName('')
      setSlug('')
      setDescription('')
      setErrors({})
    }
    onOpenChange(nextOpen)
  }

  return (
    <>
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create New Project</DialogTitle>
            <DialogDescription>
              Set up a new authentication project for your application.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-sm font-medium">Project Name</label>
              <Input
                value={name}
                onChange={(e) => handleNameChange(e.target.value)}
                placeholder="My App"
                className="mt-1.5"
                autoFocus
              />
              {errors.name && <p className="text-sm text-destructive mt-1">{errors.name}</p>}
            </div>

            <div>
              <label className="text-sm font-medium">Slug</label>
              <Input
                value={slug}
                onChange={(e) => setSlug(e.target.value)}
                placeholder="my-app"
                className="mt-1.5 font-mono"
              />
              <p className="text-xs text-muted-foreground mt-1">
                Used in URLs and API calls
              </p>
              {errors.slug && <p className="text-sm text-destructive mt-1">{errors.slug}</p>}
            </div>

            <div>
              <label className="text-sm font-medium">Description (optional)</label>
              <Input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief description of your project"
                className="mt-1.5"
              />
            </div>

            {errors.submit && (
              <p className="text-sm text-destructive bg-destructive/10 px-3 py-2 rounded">
                {errors.submit}
              </p>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Create Project
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* API Key Display Modal - shows after project creation */}
      {createdKey && (
        <ApiKeyDisplayModal
          open={keyModalOpen}
          onOpenChange={(nextOpen) => {
            setKeyModalOpen(nextOpen)
            // When modal closes, navigate to the new project
            if (!nextOpen && createdKey.projectId) {
              navigate({ to: '/projects/$projectId', params: { projectId: createdKey.projectId } })
              setCreatedKey(null)
            }
          }}
          keyName={createdKey.name}
          secretKey={createdKey.secretKey}
          publishableKey={createdKey.publishableKey}
        />
      )}
    </>
  )
}
