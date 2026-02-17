import { useParams, useNavigate } from '@tanstack/react-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  ArrowLeft,
  Copy,
  Key,
  RotateCw,
  Trash2,
  Plus,
  Check,
  Loader2,
  AlertCircle,
  Pencil,
  Info,
} from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useProject, useDeleteProject } from '@/hooks/useProjects'
import { useProjectKeys, useRotateKey, useRevokeKey, useCreateKey } from '@/hooks/useKeys'
import { UpdateProjectModal } from '@/components/UpdateProjectModal'
import { ApiKeyDisplayModal } from '@/components/ApiKeyDisplayModal'
import { OidcConfigCard } from '@/components/OidcConfigCard'
import { AuthMethodsCard } from '@/components/AuthMethodsCard'
import { OAuthProviderSetup } from '@/components/OAuthProviderSetup'
import { WebhooksCard } from '@/components/WebhooksCard'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

export function ProjectDetailPage() {
  const navigate = useNavigate()
  const { projectId } = useParams({ from: '/projects/$projectId' })
  const { data: project, isLoading: projectLoading, error: projectError } = useProject(projectId)
  const { data: keys, isLoading: keysLoading } = useProjectKeys(projectId)

  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [pendingKeyId, setPendingKeyId] = useState<string | null>(null) // Per-row loading state

  // State for API key display modal
  const [keyModalOpen, setKeyModalOpen] = useState(false)
  const [newKeyData, setNewKeyData] = useState<{ name: string; secretKey: string; publishableKey?: string } | null>(null)

  // State for OAuth provider setup modal
  const [providerSetupOpen, setProviderSetupOpen] = useState(false)
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null)

  const rotateMutation = useRotateKey(projectId)
  const revokeMutation = useRevokeKey(projectId)
  const createMutation = useCreateKey(projectId)
  const deleteMutation = useDeleteProject()

  const handleCopy = (text: string, keyId: string) => {
    navigator.clipboard.writeText(text)
    setCopiedKey(keyId)
    setTimeout(() => setCopiedKey(null), 2000)
  }

  const handleRotate = async (keyId: string) => {
    setPendingKeyId(keyId)
    try {
      const result = await rotateMutation.mutateAsync(keyId)
      // Show the new secret in modal
      setNewKeyData({
        name: result.keyPair.apiKey.name + ' (Rotated)',
        secretKey: result.keyPair.secretKey,
        publishableKey: result.keyPair.apiKey.publishableKey,
      })
      setKeyModalOpen(true)
    } catch (error) {
      console.error('Rotate failed:', error)
    } finally {
      setPendingKeyId(null)
    }
  }

  const handleRevoke = async (keyId: string) => {
    if (!confirm('Are you sure you want to revoke this key? This action cannot be undone.')) {
      return
    }
    setPendingKeyId(keyId)
    try {
      await revokeMutation.mutateAsync({ keyId })
    } catch (error) {
      console.error('Revoke failed:', error)
    } finally {
      setPendingKeyId(null)
    }
  }

  const handleCreateKey = async () => {
    try {
      const result = await createMutation.mutateAsync({ name: 'New Key', keyType: 'TEST' })
      // Show the new secret in modal
      setNewKeyData({
        name: result.keyPair.apiKey.name,
        secretKey: result.keyPair.secretKey,
        publishableKey: result.keyPair.apiKey.publishableKey,
      })
      setKeyModalOpen(true)
    } catch (error) {
      console.error('Create failed:', error)
    }
  }

  if (projectLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (projectError || !project) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h2 className="text-xl font-semibold">Project not found</h2>
        <Link to="/" className="text-primary hover:underline mt-4">
          Go back to projects
        </Link>
      </div>
    )
  }

  const keysList = keys || []

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Link to="/">
          <Button variant="ghost" size="icon">
            <ArrowLeft className="h-5 w-5" />
          </Button>
        </Link>
        <div className="flex-1">
          <h1 className="text-3xl font-bold tracking-tight">{project.name}</h1>
          <p className="text-muted-foreground font-mono text-sm mt-1">
            {project.slug}
          </p>
        </div>
        <Button variant="outline" onClick={() => setEditModalOpen(true)}>
          <Pencil className="h-4 w-4" />
          Edit
        </Button>
        <Button
          variant="outline"
          className="text-destructive hover:bg-destructive/10"
          onClick={() => setDeleteDialogOpen(true)}
        >
          <Trash2 className="h-4 w-4" />
          Delete
        </Button>
        <span
          className={`px-3 py-1.5 rounded-full text-sm font-medium ${project.environment === 'PRODUCTION'
            ? 'bg-success/20 text-success'
            : 'bg-warning/20 text-warning'
            }`}
        >
          {project.environment}
        </span>
      </div>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Project</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete <strong>{project.name}</strong>?
              This will disable all API keys immediately. You can restore the project within 7 days.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={async () => {
                try {
                  await deleteMutation.mutateAsync(projectId)
                  navigate({ to: '/' })
                } catch (error) {
                  console.error('Delete failed:', error)
                }
              }}
            >
              {deleteMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Delete Project
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>


      {/* Project Info */}
      <Card>
        <CardHeader>
          <CardTitle>Project Settings</CardTitle>
          <CardDescription>Configure your project details and security settings</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="text-sm font-medium">Project Name</label>
              <Input value={project.name} className="mt-1.5" readOnly />
            </div>
            <div>
              <label className="text-sm font-medium">Slug</label>
              <Input value={project.slug} className="mt-1.5 font-mono" readOnly />
            </div>
          </div>
          <div>
            <label className="text-sm font-medium">Allowed Origins</label>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {(project.allowedOrigins || []).map((origin) => (
                <span key={origin} className="code-text">
                  {origin}
                </span>
              ))}
              {(!project.allowedOrigins || project.allowedOrigins.length === 0) && (
                <span className="text-sm text-muted-foreground">No origins configured</span>
              )}
            </div>
          </div>
          <div>
            <label className="text-sm font-medium">Redirect URIs</label>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {(project.redirectUris || []).map((uri) => (
                <span key={uri} className="code-text">
                  {uri}
                </span>
              ))}
              {(!project.redirectUris || project.redirectUris.length === 0) && (
                <span className="text-sm text-muted-foreground">No redirect URIs configured</span>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Authentication Methods */}
      <AuthMethodsCard
        projectId={projectId}
        onConfigureProvider={(provider) => {
          setSelectedProvider(provider)
          setProviderSetupOpen(true)
        }}
      />

      {/* OIDC Configuration */}
      {project.oidcConfig && (
        <OidcConfigCard config={project.oidcConfig} projectName={project.name} />
      )}

      {/* Webhooks */}
      <WebhooksCard projectId={projectId} />

      {/* API Keys */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-xl font-semibold">API Keys</h2>
            <p className="text-sm text-muted-foreground">
              Manage authentication keys for this project
            </p>
          </div>
          <Button onClick={handleCreateKey} disabled={createMutation.isPending}>
            {createMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Plus className="h-4 w-4" />
            )}
            New Key
          </Button>
        </div>

        {/* Info banner about secret keys */}
        <div className="flex items-start gap-3 p-4 bg-muted/50 rounded-lg border mb-4">
          <Info className="h-5 w-5 text-muted-foreground mt-0.5 shrink-0" />
          <div className="text-sm">
            <p className="font-medium">Secret keys are shown only once</p>
            <p className="text-muted-foreground mt-1">
              If you don't have your secret key saved, use <strong>Rotate</strong> to generate a new one
              or <strong>+ New Key</strong> to create an additional key pair.
            </p>
          </div>
        </div>

        {keysLoading ? (
          <div className="flex justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-primary" />
          </div>
        ) : keysList.length === 0 ? (
          <Card className="border-dashed">
            <CardContent className="flex flex-col items-center justify-center py-12 text-center">
              <Key className="h-12 w-12 text-muted-foreground mb-4" />
              <p className="font-medium">No API keys yet</p>
              <p className="text-sm text-muted-foreground mt-1">
                Create your first API key to start authenticating
              </p>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {keysList.map((key) => (
              <Card key={key.id} className="overflow-hidden">
                <CardContent className="p-6">
                  <div className="flex flex-col lg:flex-row lg:items-center gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <Key className="h-4 w-4 text-muted-foreground" />
                        <span className="font-medium">{key.name}</span>
                        {key.status === 'ROTATING' ? (
                          <span className="px-2 py-0.5 rounded text-xs font-medium bg-warning/20 text-warning">
                            EXPIRING
                            {key.gracePeriodEndsAt && (
                              <span className="ml-1 opacity-75">
                                ({(() => {
                                  const end = new Date(key.gracePeriodEndsAt)
                                  const now = new Date()
                                  const msLeft = Math.max(0, end.getTime() - now.getTime())
                                  const minutesLeft = Math.ceil(msLeft / (1000 * 60))
                                  const hoursLeft = Math.ceil(msLeft / (1000 * 60 * 60))
                                  const daysLeft = Math.ceil(msLeft / (1000 * 60 * 60 * 24))
                                  if (minutesLeft <= 0) return 'soon'
                                  if (minutesLeft < 60) return `${minutesLeft}m left`
                                  if (hoursLeft < 24) return `${hoursLeft}h left`
                                  return `${daysLeft}d left`
                                })()})
                              </span>
                            )}
                          </span>
                        ) : (
                          <span
                            className={`px-2 py-0.5 rounded text-xs font-medium ${key.status === 'ACTIVE'
                              ? 'bg-success/20 text-success'
                              : 'bg-destructive/20 text-destructive'
                              }`}
                          >
                            {key.status}
                          </span>
                        )}
                      </div>
                      <div className="mt-2 space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground w-24">Publishable:</span>
                          <code className="text-xs bg-muted px-2 py-1 rounded font-mono flex-1 truncate">
                            {key.publishableKey}
                          </code>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            onClick={() => handleCopy(key.publishableKey, `pk-${key.id}`)}
                          >
                            {copiedKey === `pk-${key.id}` ? (
                              <Check className="h-3 w-3 text-success" />
                            ) : (
                              <Copy className="h-3 w-3" />
                            )}
                          </Button>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground w-24">Secret:</span>
                          <code className="text-xs bg-muted px-2 py-1 rounded font-mono flex-1">
                            {key.secretKeyMasked}
                          </code>
                          <div className="relative group h-7 w-7 flex items-center justify-center">
                            <Info className="h-3 w-3 text-muted-foreground" />
                            <div className="absolute bottom-full mb-2 right-0 hidden group-hover:block bg-popover text-popover-foreground px-2 py-1 rounded text-xs shadow-lg whitespace-nowrap">
                              Secret keys are shown only once on creation/rotation
                            </div>
                          </div>
                        </div>
                      </div>
                      {key.status === 'ROTATING' && (
                        <p className="text-xs text-warning mt-2">
                          ⚠️ Old key — will stop working soon. Use the rotated key instead.
                        </p>
                      )}
                      {key.lastUsedAt && (
                        <p className="text-xs text-muted-foreground mt-2">
                          Last used: {new Date(key.lastUsedAt).toLocaleDateString()}
                        </p>
                      )}
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleRotate(key.id)}
                        disabled={pendingKeyId !== null || key.status !== 'ACTIVE'}
                      >
                        {pendingKeyId === key.id && rotateMutation.isPending ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <RotateCw className="h-4 w-4" />
                        )}
                        Rotate
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="text-destructive hover:text-destructive"
                        onClick={() => handleRevoke(key.id)}
                        disabled={pendingKeyId !== null || key.status === 'REVOKED'}
                      >
                        {pendingKeyId === key.id && revokeMutation.isPending ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Trash2 className="h-4 w-4" />
                        )}
                        Revoke
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* Edit Project Modal */}
      {project && (
        <UpdateProjectModal
          project={project}
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
        />
      )}

      {/* API Key Display Modal */}
      {newKeyData && (
        <ApiKeyDisplayModal
          open={keyModalOpen}
          onOpenChange={setKeyModalOpen}
          keyName={newKeyData.name}
          secretKey={newKeyData.secretKey}
          publishableKey={newKeyData.publishableKey}
        />
      )}

      {/* OAuth Provider Setup Modal */}
      <OAuthProviderSetup
        projectId={projectId}
        provider={selectedProvider}
        open={providerSetupOpen}
        onOpenChange={setProviderSetupOpen}
      />
    </div>
  )
}
