import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Webhook as WebhookIcon,
  Plus,
  Trash2,
  Zap,
  Copy,
  Check,
  Loader2,
  Globe,
} from 'lucide-react'
import { useState, useCallback } from 'react'
import { useApiClient, type Webhook, type WebhookCreated } from '@/lib/api'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

// ─── Supported Events ───────────────────────────────────────────
const WEBHOOK_EVENTS = [
  { value: 'user.created', label: 'User Created', description: 'When a new user registers' },
  { value: 'user.updated', label: 'User Updated', description: 'When user profile changes' },
  { value: 'user.deleted', label: 'User Deleted', description: 'When a user is deleted' },
  { value: 'user.blocked', label: 'User Blocked', description: 'When a user is blocked' },
  { value: 'user.unblocked', label: 'User Unblocked', description: 'When block is removed' },
  { value: 'session.created', label: 'Session Created', description: 'When a user signs in' },
  { value: 'password.changed', label: 'Password Changed', description: 'When password is updated' },
  { value: '*', label: 'All Events', description: 'Receive all events' },
]

interface WebhooksCardProps {
  projectId: string
}

export function WebhooksCard({ projectId }: WebhooksCardProps) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  // ─── State ──────────────────────────────────────────────────
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [editingWebhook, setEditingWebhook] = useState<Webhook | null>(null)
  const [newSecretModal, setNewSecretModal] = useState(false)
  const [newSecret, setNewSecret] = useState<string | null>(null)
  const [copiedSecret, setCopiedSecret] = useState(false)
  const [testResults, setTestResults] = useState<Record<string, { success: boolean; message: string; timestamp: string }>>({})

  // Form state
  const [formUrl, setFormUrl] = useState('')
  const [formDescription, setFormDescription] = useState('')
  const [formEvents, setFormEvents] = useState<string[]>([])

  // ─── Queries ────────────────────────────────────────────────
  const { data: webhooks = [], isLoading } = useQuery({
    queryKey: ['webhooks', projectId],
    queryFn: () => api.listWebhooks(projectId),
  })

  // ─── Mutations ──────────────────────────────────────────────
  const createMutation = useMutation({
    mutationFn: (data: { url: string; events: string[]; description?: string }) =>
      api.createWebhook(projectId, data),
    onSuccess: (created: WebhookCreated) => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] })
      setCreateDialogOpen(false)
      resetForm()
      // Show signing secret
      setNewSecret(created.signingSecret)
      setNewSecretModal(true)
      toast.success('Webhook created')
    },
    onError: () => toast.error('Failed to create webhook'),
  })

  const updateMutation = useMutation({
    mutationFn: (data: { webhookId: string; url?: string; events?: string[]; description?: string; enabled?: boolean }) =>
      api.updateWebhook(projectId, data.webhookId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] })
      setEditDialogOpen(false)
      setEditingWebhook(null)
      resetForm()
      toast.success('Webhook updated')
    },
    onError: () => toast.error('Failed to update webhook'),
  })

  const deleteMutation = useMutation({
    mutationFn: (webhookId: string) => api.deleteWebhook(projectId, webhookId),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['webhooks', projectId] }); toast.success('Webhook deleted') },
    onError: () => toast.error('Failed to delete webhook'),
  })

  const testMutation = useMutation({
    mutationFn: (webhookId: string) => api.testWebhook(projectId, webhookId),
    onSuccess: (result, webhookId) => {
      setTestResults((current) => ({
        ...current,
        [webhookId]: {
          success: result.success,
          message: result.message,
          timestamp: new Date().toISOString(),
        },
      }))
      if (result.success) toast.success('Webhook test succeeded')
      else toast.error('Webhook test failed')
    },
    onError: () => toast.error('Webhook test failed'),
  })

  // ─── Helpers ────────────────────────────────────────────────
  const resetForm = () => {
    setFormUrl('')
    setFormDescription('')
    setFormEvents([])
  }

  const openEdit = useCallback((webhook: Webhook) => {
    setEditingWebhook(webhook)
    setFormUrl(webhook.url)
    setFormDescription(webhook.description || '')
    setFormEvents(webhook.events ? webhook.events.split(',').map(e => e.trim()).filter(Boolean) : [])
    setEditDialogOpen(true)
  }, [])

  const toggleEvent = (eventValue: string) => {
    if (eventValue === '*') {
      // Wildcard: toggle all
      setFormEvents(prev => prev.includes('*') ? [] : ['*'])
      return
    }
    setFormEvents(prev => {
      const without = prev.filter(e => e !== '*') // Remove wildcard if individual toggled
      return without.includes(eventValue)
        ? without.filter(e => e !== eventValue)
        : [...without, eventValue]
    })
  }

  const handleToggleEnabled = (webhook: Webhook) => {
    updateMutation.mutate({ webhookId: webhook.id, enabled: !webhook.enabled })
  }

  const copySecret = () => {
    if (newSecret) {
      navigator.clipboard.writeText(newSecret)
      setCopiedSecret(true)
      toast.success('Copied to clipboard')
      setTimeout(() => setCopiedSecret(false), 2000)
    }
  }

  const formatEventList = (events: string) => {
    if (!events) return []
    return events.split(',').map(e => e.trim()).filter(Boolean)
  }

  // ─── Render ─────────────────────────────────────────────────
  return (
    <>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <div>
            <CardTitle className="flex items-center gap-2">
              <WebhookIcon className="h-5 w-5" />
              Webhooks
            </CardTitle>
            <CardDescription>
              Receive real-time notifications when auth events occur
            </CardDescription>
          </div>
          <Button
            size="sm"
            onClick={() => { resetForm(); setCreateDialogOpen(true) }}
          >
            <Plus className="h-4 w-4" />
            Add Webhook
          </Button>
        </CardHeader>
        <CardContent>
          {isLoading ? <div className="flex justify-center py-8"><Loader2 className="size-5 animate-spin text-primary" /></div> : webhooks.length === 0 ? <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border py-12 text-center"><Globe className="mb-3 size-9 text-foreground-muted" /><p className="text-sm font-medium">No webhooks configured</p><p className="mt-1 text-sm text-foreground-secondary">Add a webhook to receive auth event notifications.</p></div> : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-b border-border bg-surface-raised text-xs text-foreground-muted"><tr><th className="px-4 py-3 font-medium">Endpoint</th><th className="px-4 py-3 font-medium">Events</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Last test</th><th className="px-4 py-3 font-medium">Actions</th></tr></thead>
                <tbody>{webhooks.map((webhook) => {
                  const events = formatEventList(webhook.events)
                  const lastTest = testResults[webhook.id]
                  return <tr key={webhook.id} onClick={() => openEdit(webhook)} className="cursor-pointer border-b border-border last:border-b-0 transition-colors duration-150 hover:bg-surface-raised"><td className="max-w-72 px-4 py-3"><code className="block truncate font-mono text-xs text-foreground">{webhook.url}</code>{webhook.description ? <p className="mt-1 truncate text-xs text-foreground-muted">{webhook.description}</p> : null}</td><td className="px-4 py-3 text-xs text-foreground-secondary">{events.length || 0} event{events.length === 1 ? '' : 's'}</td><td className="px-4 py-3"><span className={webhook.enabled ? 'inline-flex items-center gap-1.5 text-xs text-success' : 'inline-flex items-center gap-1.5 text-xs text-foreground-muted'}><span className={webhook.enabled ? 'size-1.5 rounded-full bg-success' : 'size-1.5 rounded-full bg-foreground-muted'} />{webhook.enabled ? 'Enabled' : 'Disabled'}</span></td><td className={lastTest?.success ? 'px-4 py-3 text-xs text-success' : lastTest ? 'px-4 py-3 text-xs text-danger' : 'px-4 py-3 text-xs text-foreground-muted'}>{lastTest ? `${lastTest.success ? 'Succeeded' : 'Failed'} · ${new Date(lastTest.timestamp).toLocaleTimeString()}` : 'Not tested'}</td><td className="px-4 py-3" onClick={(event) => event.stopPropagation()}><div className="flex items-center gap-1"><Switch checked={webhook.enabled} onCheckedChange={() => handleToggleEnabled(webhook)} disabled={updateMutation.isPending} /><Button variant="ghost" size="icon" className="size-7" onClick={() => testMutation.mutate(webhook.id)} disabled={!webhook.enabled || testMutation.isPending} aria-label="Test webhook">{testMutation.isPending && testMutation.variables === webhook.id ? <Loader2 className="size-3.5 animate-spin" /> : <Zap className="size-3.5" />}</Button><Button variant="ghost" size="icon" className="size-7 text-danger hover:text-danger" onClick={() => { if (confirm('Delete this webhook? This cannot be undone.')) deleteMutation.mutate(webhook.id) }} disabled={deleteMutation.isPending} aria-label="Delete webhook"><Trash2 className="size-3.5" /></Button></div></td></tr>
                })}</tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* ─── Create Webhook Dialog ──────────────────────────────── */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Add Webhook</DialogTitle>
            <DialogDescription>
              Configure a URL to receive auth event notifications via HTTP POST
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label htmlFor="webhook-url">Endpoint URL</Label>
              <Input
                id="webhook-url"
                placeholder="https://api.yourapp.com/webhooks/arkil"
                value={formUrl}
                onChange={(e) => setFormUrl(e.target.value)}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="webhook-desc">Description (optional)</Label>
              <Input
                id="webhook-desc"
                placeholder="Production notification handler"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label className="mb-2 block">Events</Label>
              <div className="grid grid-cols-1 gap-2 max-h-[240px] overflow-y-auto pr-1">
                {WEBHOOK_EVENTS.map((event) => (
                  <label
                    key={event.value}
                    className={`flex items-center gap-3 p-2.5 rounded-lg border cursor-pointer transition-colors hover:bg-muted/50 ${formEvents.includes(event.value) ? 'border-primary bg-primary/5' : 'border-border'
                      }`}
                  >
                    <input
                      type="checkbox"
                      checked={formEvents.includes(event.value) || formEvents.includes('*')}
                      onChange={() => toggleEvent(event.value)}
                      className="rounded border-border"
                    />
                    <div>
                      <span className="text-sm font-medium">{event.label}</span>
                      <span className="text-xs text-muted-foreground ml-2">{event.description}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => createMutation.mutate({
                url: formUrl,
                events: formEvents,
                description: formDescription || undefined,
              })}
              disabled={!formUrl || formEvents.length === 0 || createMutation.isPending}
            >
              {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Create Webhook
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ─── Edit Webhook Dialog ────────────────────────────────── */}
      <Dialog open={editDialogOpen} onOpenChange={(open) => {
        setEditDialogOpen(open)
        if (!open) setEditingWebhook(null)
      }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Edit Webhook</DialogTitle>
            <DialogDescription>Update webhook configuration</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label htmlFor="edit-url">Endpoint URL</Label>
              <Input
                id="edit-url"
                value={formUrl}
                onChange={(e) => setFormUrl(e.target.value)}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="edit-desc">Description</Label>
              <Input
                id="edit-desc"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label className="mb-2 block">Events</Label>
              <div className="grid grid-cols-1 gap-2 max-h-[240px] overflow-y-auto pr-1">
                {WEBHOOK_EVENTS.map((event) => (
                  <label
                    key={event.value}
                    className={`flex items-center gap-3 p-2.5 rounded-lg border cursor-pointer transition-colors hover:bg-muted/50 ${formEvents.includes(event.value) ? 'border-primary bg-primary/5' : 'border-border'
                      }`}
                  >
                    <input
                      type="checkbox"
                      checked={formEvents.includes(event.value) || formEvents.includes('*')}
                      onChange={() => toggleEvent(event.value)}
                      className="rounded border-border"
                    />
                    <div>
                      <span className="text-sm font-medium">{event.label}</span>
                      <span className="text-xs text-muted-foreground ml-2">{event.description}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => editingWebhook && updateMutation.mutate({
                webhookId: editingWebhook.id,
                url: formUrl,
                events: formEvents,
                description: formDescription || undefined,
              })}
              disabled={!formUrl || formEvents.length === 0 || updateMutation.isPending}
            >
              {updateMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Save Changes
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ─── Signing Secret Modal ───────────────────────────────── */}
      <Dialog open={newSecretModal} onOpenChange={setNewSecretModal}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Webhook Created</DialogTitle>
            <DialogDescription>
              Copy your signing secret now — it won't be shown again.
              Use this to verify webhook payloads with HMAC-SHA256.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <Label>Signing Secret</Label>
            <div className="flex items-center gap-2">
              <code className="flex-1 bg-muted px-3 py-2 rounded-md font-mono text-sm break-all">
                {newSecret}
              </code>
              <Button variant="outline" size="icon" onClick={copySecret}>
                {copiedSecret ? (
                  <Check className="h-4 w-4 text-success" />
                ) : (
                  <Copy className="h-4 w-4" />
                )}
              </Button>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => { setNewSecretModal(false); setNewSecret(null) }}>
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
