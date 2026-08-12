import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { Fragment, useState } from 'react'
import { toast } from 'sonner'
import {
  AlertCircle, ArrowLeft, Copy, Ellipsis, Globe2, Info, KeyRound, Loader2, Pencil, Plus, RotateCw, Settings2, Trash2,
} from 'lucide-react'
import { ApiKeyDisplayModal } from '@/components/ApiKeyDisplayModal'
import { AuthMethodsCard } from '@/components/AuthMethodsCard'
import { OAuthProviderSetup } from '@/components/OAuthProviderSetup'
import { OidcConfigCard } from '@/components/OidcConfigCard'
import { UpdateProjectModal } from '@/components/UpdateProjectModal'
import { WebhooksCard } from '@/components/WebhooksCard'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { EmptyState } from '@/components/ui/empty-state'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useAuthMethods } from '@/hooks/useAuthPolicy'
import { useCreateKey, useProjectKeys, useRevokeKey, useRotateKey } from '@/hooks/useKeys'
import { useDeleteProject, useProject } from '@/hooks/useProjects'

function formatDate(value?: string) {
  if (!value) return 'Never'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Unknown' : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function rotatingCountdown(value?: string) {
  if (!value) return 'Rotating'
  const minutes = Math.max(0, Math.ceil((new Date(value).getTime() - Date.now()) / 60000))
  if (minutes < 60) return `${minutes}m left`
  if (minutes < 1440) return `${Math.ceil(minutes / 60)}h left`
  return `${Math.ceil(minutes / 1440)}d left`
}

function EnvironmentBadge({ environment }: { environment: 'DEVELOPMENT' | 'PRODUCTION' }) {
  const production = environment === 'PRODUCTION'
  return <span className={production ? 'inline-flex items-center gap-1.5 text-xs font-medium text-success' : 'inline-flex items-center gap-1.5 text-xs font-medium text-warning'}><span className={production ? 'size-1.5 rounded-full bg-success' : 'size-1.5 rounded-full bg-warning'} />{production ? 'Production' : 'Development'}</span>
}

function KeyStatus({ status, gracePeriodEndsAt }: { status: 'ACTIVE' | 'ROTATING' | 'REVOKED' | 'EXPIRED'; gracePeriodEndsAt?: string }) {
  const tone = status === 'ACTIVE' ? 'text-success' : status === 'ROTATING' ? 'text-warning' : 'text-danger'
  const dot = status === 'ACTIVE' ? 'bg-success' : status === 'ROTATING' ? 'bg-warning' : 'bg-danger'
  const label = status === 'ROTATING' ? `Rotating · ${rotatingCountdown(gracePeriodEndsAt)}` : status.charAt(0) + status.slice(1).toLowerCase()
  return <span className={`inline-flex items-center gap-1.5 whitespace-nowrap text-xs ${tone}`}><span className={`size-1.5 rounded-full ${dot}`} />{label}</span>
}

function CopyList({ title, values, emptyLabel }: { title: string; values: string[]; emptyLabel: string }) {
  const copy = (value: string) => void navigator.clipboard.writeText(value).then(() => toast.success('Copied to clipboard'))
  return (
    <div><h3 className="mb-2 text-sm font-medium text-foreground">{title}</h3>{values.length ? <div className="overflow-hidden rounded-lg border border-border">{values.map((value, index) => <div key={value} className={index ? 'flex items-center gap-3 border-t border-border px-3 py-2.5' : 'flex items-center gap-3 px-3 py-2.5'}><Globe2 className="size-4 shrink-0 text-foreground-muted" /><code className="min-w-0 flex-1 truncate font-mono text-xs text-foreground-secondary">{value}</code><Button variant="ghost" size="icon" className="size-7 shrink-0" onClick={() => copy(value)} aria-label={`Copy ${value}`}><Copy className="size-3.5" /></Button></div>)}</div> : <p className="text-sm text-foreground-muted">{emptyLabel}</p>}</div>
  )
}

function DetailSkeleton() {
  return <div className="space-y-6"><div className="flex items-center gap-4"><Skeleton className="size-12" /><div className="space-y-2"><Skeleton className="h-6 w-48" /><Skeleton className="h-3 w-32" /></div></div><Skeleton className="h-10 w-full" /><Card><CardContent className="space-y-3 py-5"><Skeleton className="h-5 w-1/3" /><Skeleton className="h-4 w-full" /><Skeleton className="h-4 w-4/5" /></CardContent></Card></div>
}

export function ProjectDetailPage() {
  const navigate = useNavigate()
  const { projectId } = useParams({ from: '/projects/$projectId' })
  const { data: project, isLoading: projectLoading, error: projectError } = useProject(projectId)
  const { data: keys, isLoading: keysLoading } = useProjectKeys(projectId)
  const { data: authMethods } = useAuthMethods(projectId)
  const rotateMutation = useRotateKey(projectId)
  const revokeMutation = useRevokeKey(projectId)
  const createMutation = useCreateKey(projectId)
  const deleteMutation = useDeleteProject()

  const [editModalOpen, setEditModalOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [createKeyDialogOpen, setCreateKeyDialogOpen] = useState(false)
  const [pendingKeyId, setPendingKeyId] = useState<string | null>(null)
  const [expandedKeyId, setExpandedKeyId] = useState<string | null>(null)
  const [newKeyName, setNewKeyName] = useState('')
  const [newKeyType, setNewKeyType] = useState<'TEST' | 'LIVE'>('TEST')
  const [keyActionError, setKeyActionError] = useState('')
  const [keyModalOpen, setKeyModalOpen] = useState(false)
  const [newKeyData, setNewKeyData] = useState<{ name: string; secretKey: string; publishableKey?: string } | null>(null)
  const [providerSetupOpen, setProviderSetupOpen] = useState(false)
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null)

  const copyKey = (value: string) => void navigator.clipboard.writeText(value).then(() => toast.success('Copied to clipboard'))
  const handleRotate = async (keyId: string) => { setPendingKeyId(keyId); try { const result = await rotateMutation.mutateAsync(keyId); setNewKeyData({ name: `${result.keyPair.apiKey.name} (Rotated)`, secretKey: result.keyPair.secretKey, publishableKey: result.keyPair.apiKey.publishableKey }); setKeyModalOpen(true); toast.success('API key rotated') } catch (error) { console.error('Rotate failed:', error); toast.error('Failed to rotate API key') } finally { setPendingKeyId(null) } }
  const handleRevoke = async (keyId: string) => { if (!confirm('Are you sure you want to revoke this key? This action cannot be undone.')) return; setPendingKeyId(keyId); try { await revokeMutation.mutateAsync({ keyId }); toast.success('API key revoked') } catch (error) { console.error('Revoke failed:', error); toast.error('Failed to revoke API key') } finally { setPendingKeyId(null) } }
  const handleCreateKey = async () => { try { const result = await createMutation.mutateAsync({ name: newKeyName.trim() || `${project?.name || 'Project'} ${newKeyType === 'LIVE' ? 'Live' : 'Test'} Key`, keyType: newKeyType }); setNewKeyData({ name: result.keyPair.apiKey.name, secretKey: result.keyPair.secretKey, publishableKey: result.keyPair.apiKey.publishableKey }); setCreateKeyDialogOpen(false); setNewKeyName(''); setNewKeyType('TEST'); setKeyActionError(''); setKeyModalOpen(true); toast.success('API key created') } catch (error) { setKeyActionError(error instanceof Error ? error.message : 'Create failed'); toast.error('Failed to create API key') } }

  if (projectLoading) return <DetailSkeleton />
  if (projectError || !project) return <div className="flex min-h-96 flex-col items-center justify-center text-center"><AlertCircle className="size-10 text-danger" /><h2 className="mt-3 text-lg font-semibold">Project not found</h2><Link to="/" className="mt-3 text-sm font-medium text-primary hover:underline">Go back to projects</Link></div>

  const keysList = keys || []
  const enabled = new Set(authMethods?.enabledModules ?? [])
  const authStatusRows = [
    ['Email / Password', 'EMAIL_PASSWORD'], ['Magic Link', 'MAGIC_LINK'], ['Passkey', 'PASSKEY'], ['Authenticator app', 'TOTP'],
  ] as const

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <Link to="/"><Button variant="ghost" size="icon" aria-label="Back to projects"><ArrowLeft className="size-4" /></Button></Link>
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <div className="flex size-12 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-surface-raised text-base font-semibold text-foreground-secondary">{project.iconUrl ? <img src={project.iconUrl} alt="" className="size-full object-cover" /> : project.name.charAt(0).toUpperCase()}</div>
          <div className="min-w-0"><div className="flex flex-wrap items-center gap-x-3 gap-y-1"><h1 className="truncate text-xl font-semibold tracking-tight text-foreground">{project.name}</h1><EnvironmentBadge environment={project.environment} /></div><p className="mt-1 truncate font-mono text-xs text-foreground-muted">{project.slug}</p></div>
        </div>
        <div className="flex items-center gap-2 self-end sm:self-auto"><Button variant="outline" size="sm" onClick={() => setEditModalOpen(true)}><Pencil className="size-3.5" />Edit</Button><Button variant="outline" size="sm" className="text-danger hover:text-danger" onClick={() => setDeleteDialogOpen(true)}><Trash2 className="size-3.5" />Delete</Button></div>
      </div>

      <Tabs defaultValue="overview">
        <div className="overflow-x-auto pb-1"><TabsList className="w-max bg-transparent p-0"><TabsTrigger value="overview" className="data-[state=active]:border-b-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none">Overview</TabsTrigger><TabsTrigger value="authentication" className="data-[state=active]:border-b-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none">Authentication</TabsTrigger><TabsTrigger value="keys" className="data-[state=active]:border-b-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none">API Keys</TabsTrigger><TabsTrigger value="webhooks" className="data-[state=active]:border-b-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none">Webhooks</TabsTrigger><TabsTrigger value="integration" className="data-[state=active]:border-b-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none">Integration</TabsTrigger><TabsTrigger value="settings" className="data-[state=active]:border-b-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none">Settings</TabsTrigger></TabsList></div>

        <TabsContent value="overview" className="space-y-5">
          <Card><CardHeader><CardTitle>Project identity</CardTitle><CardDescription>Core details for this authentication environment.</CardDescription></CardHeader><CardContent className="grid gap-4 sm:grid-cols-2"><div><p className="text-xs font-medium text-foreground-muted">Name</p><p className="mt-1 text-sm text-foreground">{project.name}</p></div><div><p className="text-xs font-medium text-foreground-muted">Slug</p><p className="mt-1 font-mono text-xs text-foreground">{project.slug}</p></div><div><p className="text-xs font-medium text-foreground-muted">Environment</p><div className="mt-1"><EnvironmentBadge environment={project.environment} /></div></div><div><p className="text-xs font-medium text-foreground-muted">Description</p><p className="mt-1 text-sm text-foreground-secondary">{project.description || 'No description provided.'}</p></div></CardContent></Card>
          <div className="grid gap-5 lg:grid-cols-2"><Card><CardContent className="py-5"><CopyList title="Allowed origins" values={project.allowedOrigins || []} emptyLabel="No origins configured." /></CardContent></Card><Card><CardContent className="py-5"><CopyList title="Redirect URIs" values={project.redirectUris || []} emptyLabel="No redirect URIs configured." /></CardContent></Card></div>
          <Card><CardHeader><CardTitle>Authentication status</CardTitle><CardDescription>Methods currently available to your users.</CardDescription></CardHeader><CardContent className="grid gap-2 sm:grid-cols-2">{authStatusRows.map(([label, id]) => <div key={id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5"><span className="text-sm text-foreground">{label}</span><span className={enabled.has(id) ? 'inline-flex items-center gap-1.5 text-xs text-primary' : 'inline-flex items-center gap-1.5 text-xs text-foreground-muted'}><span className={enabled.has(id) ? 'size-1.5 rounded-full bg-primary' : 'size-1.5 rounded-full bg-foreground-muted'} />{enabled.has(id) ? 'Enabled' : 'Disabled'}</span></div>)}</CardContent></Card>
        </TabsContent>

        <TabsContent value="authentication"><AuthMethodsCard projectId={projectId} onConfigureProvider={(provider) => { setSelectedProvider(provider); setProviderSetupOpen(true) }} /></TabsContent>

        <TabsContent value="keys" className="min-w-0 space-y-4">
          <div className="flex items-end justify-between gap-4"><div><h2 className="text-base font-semibold">API keys</h2><p className="mt-1 text-sm text-foreground-secondary">Create, rotate, and revoke project credentials.</p></div><Button size="sm" onClick={() => { setKeyActionError(''); setCreateKeyDialogOpen(true) }}><Plus className="size-3.5" />Create key</Button></div>
          <div className="flex items-start gap-2 rounded-lg border border-border bg-surface-raised px-3 py-2.5 text-xs text-foreground-secondary"><Info className="mt-0.5 size-4 shrink-0 text-foreground-muted" />Secret keys are shown only on creation or rotation. Rotate a key if the original secret is unavailable.</div>
          {keysLoading ? <Card><CardContent className="space-y-3 py-5"><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></CardContent></Card> : keysList.length === 0 ? <EmptyState icon={KeyRound} title="No API keys yet" description="Create a test or live key to authenticate requests for this project." action={<Button size="sm" onClick={() => setCreateKeyDialogOpen(true)}><Plus className="size-3.5" />Create key</Button>} /> : <div className="overflow-x-auto rounded-lg border border-border"><table className="w-full min-w-[720px] text-left text-sm"><thead className="border-b border-border bg-surface-raised text-xs text-foreground-muted"><tr><th className="px-4 py-3 font-medium">Name</th><th className="px-4 py-3 font-medium">Type</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Last used</th><th className="w-12 px-3 py-3"><span className="sr-only">Actions</span></th></tr></thead><tbody>{keysList.map((key) =>
          <Fragment key={key.id}><tr onClick={() => setExpandedKeyId((current) => current === key.id ? null : key.id)} className="cursor-pointer border-b border-border last:border-b-0 transition-colors duration-150 hover:bg-surface-raised"><td className="px-4 py-3 font-medium text-foreground">{key.name}</td><td className="px-4 py-3"><Badge variant={key.keyType === 'LIVE' ? 'default' : 'secondary'}>{key.keyType}</Badge></td><td className="px-4 py-3"><KeyStatus status={key.status} gracePeriodEndsAt={key.gracePeriodEndsAt} /></td><td className="px-4 py-3 text-xs text-foreground-secondary">{formatDate(key.lastUsedAt)}</td><td className="px-3 py-3" onClick={(event) => event.stopPropagation()}><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-7" aria-label={`Actions for ${key.name}`}><Ellipsis className="size-4" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem disabled={pendingKeyId !== null || key.status !== 'ACTIVE'} onSelect={() => void handleRotate(key.id)}><RotateCw className="size-4" />Rotate</DropdownMenuItem><DropdownMenuItem className="text-danger focus:text-danger" disabled={pendingKeyId !== null || key.status === 'REVOKED'} onSelect={() => void handleRevoke(key.id)}><Trash2 className="size-4" />Revoke</DropdownMenuItem></DropdownMenuContent></DropdownMenu></td></tr>
            {expandedKeyId === key.id ? <tr key={`${key.id}-details`} className="border-b border-border bg-surface-raised"><td colSpan={5} className="px-4 py-4"><div className="grid gap-3 lg:grid-cols-2"><div><p className="mb-1 text-xs font-medium text-foreground-muted">Publishable key</p><div className="flex items-center gap-2 rounded-lg border border-border bg-surface px-2 py-1.5"><code className="min-w-0 flex-1 truncate font-mono text-xs">{key.publishableKey}</code><Button variant="ghost" size="icon" className="size-7" onClick={() => copyKey(key.publishableKey)} aria-label="Copy publishable key"><Copy className="size-3.5" /></Button></div></div><div><p className="mb-1 text-xs font-medium text-foreground-muted">Secret key</p><div className="flex items-center gap-2 rounded-lg border border-border bg-surface px-2 py-1.5"><code className="min-w-0 flex-1 truncate font-mono text-xs">{key.secretKeyMasked}</code><Tooltip><TooltipTrigger asChild><Info className="size-3.5 text-foreground-muted" /></TooltipTrigger><TooltipContent>Shown only on creation</TooltipContent></Tooltip></div></div><p className="text-xs text-foreground-secondary">Created {formatDate(key.createdAt)}</p><p className="text-xs text-foreground-secondary">Last used {formatDate(key.lastUsedAt)}</p></div></td></tr> : null}
          </Fragment>)}</tbody></table></div>}
        </TabsContent>

        <TabsContent value="webhooks"><WebhooksCard projectId={projectId} /></TabsContent>
        <TabsContent value="integration">{project.oidcConfig ? <OidcConfigCard config={project.oidcConfig} projectName={project.name} /> : <EmptyState icon={Settings2} title="OIDC configuration unavailable" description="OIDC endpoints will appear here once they are configured for this project." />}</TabsContent>
        <TabsContent value="settings" className="space-y-5"><Card><CardHeader><CardTitle>Project settings</CardTitle><CardDescription>Review the project configuration or edit it in a focused dialog.</CardDescription></CardHeader><CardContent className="space-y-4"><div className="grid gap-4 sm:grid-cols-2"><div><p className="text-xs font-medium text-foreground-muted">Project name</p><p className="mt-1 text-sm">{project.name}</p></div><div><p className="text-xs font-medium text-foreground-muted">Icon URL</p><p className="mt-1 truncate font-mono text-xs">{project.iconUrl || 'Not configured'}</p></div></div><Button variant="outline" onClick={() => setEditModalOpen(true)}><Pencil className="size-3.5" />Edit project</Button></CardContent></Card><Card className="border-danger/35"><CardHeader><CardTitle className="text-danger">Danger zone</CardTitle><CardDescription>Deleting disables all project API keys. The project can be restored for seven days.</CardDescription></CardHeader><CardContent><Button variant="outline" className="text-danger hover:text-danger" onClick={() => setDeleteDialogOpen(true)}><Trash2 className="size-3.5" />Delete project</Button></CardContent></Card></TabsContent>
      </Tabs>

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}><DialogContent><DialogHeader><DialogTitle>Delete project</DialogTitle><DialogDescription>Delete <strong>{project.name}</strong>? All API keys will be disabled immediately; restoration remains available for seven days.</DialogDescription></DialogHeader><DialogFooter><Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>Cancel</Button><Button variant="destructive" disabled={deleteMutation.isPending} onClick={async () => { try { await deleteMutation.mutateAsync(projectId); toast.success('Project deleted'); navigate({ to: '/' }) } catch (error) { console.error('Delete failed:', error); toast.error('Failed to delete project') } }}>{deleteMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}Delete project</Button></DialogFooter></DialogContent></Dialog>
      <UpdateProjectModal project={project} open={editModalOpen} onOpenChange={setEditModalOpen} />
      {newKeyData ? <ApiKeyDisplayModal open={keyModalOpen} onOpenChange={setKeyModalOpen} keyName={newKeyData.name} secretKey={newKeyData.secretKey} publishableKey={newKeyData.publishableKey} /> : null}
      <OAuthProviderSetup projectId={projectId} provider={selectedProvider} open={providerSetupOpen} onOpenChange={setProviderSetupOpen} />
      <Dialog open={createKeyDialogOpen} onOpenChange={(open) => { setCreateKeyDialogOpen(open); if (!open) { setNewKeyName(''); setNewKeyType('TEST'); setKeyActionError('') } }}><DialogContent className="create-api-key-dialog sm:max-w-md"><DialogHeader><DialogTitle>Create API key</DialogTitle><DialogDescription>Create a new test or live key pair for this project.</DialogDescription></DialogHeader><div className="min-w-0 space-y-4"><div><label className="text-sm font-medium">Key name</label><Input value={newKeyName} onChange={(event) => setNewKeyName(event.target.value)} className="mt-1.5" placeholder={`${project.name} ${newKeyType === 'LIVE' ? 'Live' : 'Test'} Key`} /></div><div><label className="text-sm font-medium">Key type</label><Select value={newKeyType} onValueChange={(value) => setNewKeyType(value as 'TEST' | 'LIVE')}><SelectTrigger className="mt-1.5"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TEST">Test</SelectItem><SelectItem value="LIVE">Live</SelectItem></SelectContent></Select></div>{keyActionError ? <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">{keyActionError}</p> : null}</div><DialogFooter><Button variant="outline" onClick={() => setCreateKeyDialogOpen(false)}>Cancel</Button><Button onClick={handleCreateKey} disabled={createMutation.isPending}>{createMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}Create key</Button></DialogFooter></DialogContent></Dialog>
    </div>
  )
}
