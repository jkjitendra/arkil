import { useDeferredValue, useState } from 'react'
import { AlertCircle, Check, Eye, MailCheck, MailX, Search, Shield, Trash2, UserX } from 'lucide-react'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/ui/empty-state'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAdminUser, useAdminUsers, useBlockAdminUser, useDeleteAdminUser, useUnblockAdminUser, useUpdateAdminUser } from '@/hooks/useAdminUsers'
import { useProfile } from '@/hooks/useProfile'

const PAGE_SIZE = 12

function formatDate(value?: string) {
  if (!value) return 'Never'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Unknown' : date.toLocaleString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('') || 'U'
}

function UsersTableSkeleton() {
  return <div className="overflow-hidden rounded-lg border border-border"><Table><TableHeader><TableRow><TableHead>User</TableHead><TableHead>Status</TableHead><TableHead>Email verified</TableHead><TableHead>Roles</TableHead><TableHead>Last active</TableHead><TableHead>Actions</TableHead></TableRow></TableHeader><TableBody>{Array.from({ length: 5 }, (_, index) => <TableRow key={index}><TableCell><div className="flex items-center gap-3"><Skeleton className="size-8 rounded-full" /><div className="space-y-1"><Skeleton className="h-3 w-28" /><Skeleton className="h-3 w-40" /></div></div></TableCell><TableCell><Skeleton className="h-3 w-14" /></TableCell><TableCell><Skeleton className="h-3 w-16" /></TableCell><TableCell><Skeleton className="h-5 w-20" /></TableCell><TableCell><Skeleton className="h-3 w-20" /></TableCell><TableCell><Skeleton className="h-7 w-36" /></TableCell></TableRow>)}</TableBody></Table></div>
}

export function UsersPage() {
  const { data: profile } = useProfile()
  const canManageUsers = !!profile?.roles.some((role) => ['TENANT_ADMIN', 'SUPER_ADMIN', 'PLATFORM_ADMIN'].includes(role))
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const deferredSearch = useDeferredValue(search.trim().toLowerCase())
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [actionError, setActionError] = useState('')

  const usersQuery = useAdminUsers(page, PAGE_SIZE, canManageUsers)
  const userDetail = useAdminUser(selectedUserId, canManageUsers)
  const updateUser = useUpdateAdminUser()
  const blockUser = useBlockAdminUser()
  const unblockUser = useUnblockAdminUser()
  const deleteUser = useDeleteAdminUser()

  const users = usersQuery.data?.content || []
  const filteredUsers = deferredSearch ? users.filter((user) => [user.displayName, user.username, user.email, user.tenantName, user.tenantSlug].filter(Boolean).some((value) => value!.toLowerCase().includes(deferredSearch))) : users
  const selectedUser = userDetail.data
  const isCurrentUser = !!selectedUser && selectedUser.id === profile?.id
  const isWorking = updateUser.isPending || blockUser.isPending || unblockUser.isPending || deleteUser.isPending

  const handleToggleVerification = async (userId: string, emailVerified: boolean) => { setActionError(''); try { await updateUser.mutateAsync({ userId, data: { emailVerified: !emailVerified } }) } catch (error) { setActionError(error instanceof Error ? error.message : 'Failed to update verification status') } }
  const handleToggleBlocked = async (userId: string, enabled: boolean) => { setActionError(''); try { if (enabled) await blockUser.mutateAsync({ userId, reason: 'Blocked from dashboard' }); else await unblockUser.mutateAsync(userId) } catch (error) { setActionError(error instanceof Error ? error.message : 'Failed to update account status') } }
  const handleDeleteUser = async (userId: string, email: string) => { setActionError(''); if (!window.confirm(`Disable ${email}? This preserves audit history and can be reversed later.`)) return; try { await deleteUser.mutateAsync(userId); setSelectedUserId(null) } catch (error) { setActionError(error instanceof Error ? error.message : 'Failed to disable user') } }

  if (!profile) return <UsersTableSkeleton />
  if (!canManageUsers) return <EmptyState icon={Shield} title="Users access requires an admin role" description="Sign in with a tenant admin or super admin account to manage end users." />
  if (usersQuery.isLoading) return <UsersTableSkeleton />
  if (usersQuery.error) return <div className="flex min-h-80 flex-col items-center justify-center text-center"><AlertCircle className="size-9 text-danger" /><h2 className="mt-3 text-base font-semibold">Failed to load users</h2><p className="mt-1 text-sm text-foreground-secondary">{usersQuery.error.message}</p></div>

  return (
    <div className="space-y-6">
      <PageHeader title="Users" description="Review end users in your tenant and manage account access." />
      <div className="relative max-w-md"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-foreground-muted" /><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search name, email, or tenant…" className="pl-9" aria-label="Search users" /></div>

      <Card>
        <CardHeader><CardTitle>User directory</CardTitle><p className="text-sm text-foreground-secondary">Showing {filteredUsers.length} of {users.length} users on this page.</p></CardHeader>
        <CardContent>
          {filteredUsers.length === 0 ? <EmptyState icon={Search} title="No users matched" description="Try a different name, email address, or tenant." className="min-h-56" /> : <div className="overflow-hidden rounded-lg border border-border"><Table><TableHeader><TableRow><TableHead>User</TableHead><TableHead>Status</TableHead><TableHead>Email verified</TableHead><TableHead>Roles</TableHead><TableHead>Last active</TableHead><TableHead className="text-right">Actions</TableHead></TableRow></TableHeader><TableBody>{filteredUsers.map((user) => {
            const isSelf = user.id === profile.id
            const displayName = user.displayName || user.username
            return <TableRow key={user.id}><TableCell><div className="flex min-w-52 items-center gap-3"><Avatar className="size-8 border border-border"><AvatarFallback>{initials(displayName)}</AvatarFallback></Avatar><div className="min-w-0"><p className="truncate text-sm font-medium text-foreground">{displayName}</p><p className="truncate text-xs text-foreground-muted">{user.email}</p></div></div></TableCell><TableCell><span className={user.enabled ? 'inline-flex items-center gap-1.5 text-xs text-success' : 'inline-flex items-center gap-1.5 text-xs text-danger'}><span className={user.enabled ? 'size-1.5 rounded-full bg-success' : 'size-1.5 rounded-full bg-danger'} />{user.enabled ? 'Active' : 'Blocked'}</span></TableCell><TableCell>{user.emailVerified ? <span className="inline-flex items-center gap-1.5 text-xs text-success"><MailCheck className="size-3.5" />Verified</span> : <span className="inline-flex items-center gap-1.5 text-xs text-warning"><MailX className="size-3.5" />Unverified</span>}</TableCell><TableCell><div className="flex max-w-48 flex-wrap gap-1">{user.roles.length ? user.roles.map((role) => <Badge key={role} variant="outline">{role}</Badge>) : <span className="text-xs text-foreground-muted">No roles</span>}</div></TableCell><TableCell className="whitespace-nowrap text-xs text-foreground-secondary">{formatDate(user.lastLoginAt)}</TableCell><TableCell><div className="flex justify-end gap-1"><Button variant="ghost" size="sm" onClick={() => setSelectedUserId(user.id)}><Eye className="size-3.5" />View</Button><Button variant="ghost" size="sm" onClick={() => void handleToggleVerification(user.id, user.emailVerified)} disabled={isWorking || isSelf}>{user.emailVerified ? 'Unverify' : 'Verify'}</Button><Button variant="ghost" size="sm" className="text-danger hover:text-danger" onClick={() => void handleToggleBlocked(user.id, user.enabled)} disabled={isWorking || isSelf}>{user.enabled ? 'Block' : 'Unblock'}</Button></div></TableCell></TableRow>
          })}</TableBody></Table></div>}
          <div className="mt-4 flex items-center justify-between border-t border-border pt-4"><p className="text-sm text-foreground-secondary">Page {page + 1} of {Math.max(usersQuery.data?.totalPages || 1, 1)}</p><div className="flex gap-2"><Button variant="outline" size="sm" onClick={() => setPage((current) => Math.max(current - 1, 0))} disabled={page === 0}>Previous</Button><Button variant="outline" size="sm" onClick={() => setPage((current) => current + 1)} disabled={!!usersQuery.data?.last}>Next</Button></div></div>
        </CardContent>
      </Card>

      <Sheet open={!!selectedUserId} onOpenChange={(open) => !open && setSelectedUserId(null)}>
        <SheetContent className="w-[min(100%,32rem)] overflow-y-auto">
          <SheetHeader><SheetTitle>User details</SheetTitle><SheetDescription>Review identity state and apply admin actions for this account.</SheetDescription></SheetHeader>
          {userDetail.isLoading ? <div className="space-y-3 py-6"><Skeleton className="h-24 w-full" /><Skeleton className="h-24 w-full" /><Skeleton className="h-20 w-full" /></div> : selectedUser ? <div className="mt-2 space-y-4"><Card><CardHeader><CardTitle className="text-sm">Identity</CardTitle></CardHeader><CardContent className="space-y-3 text-sm"><div><p className="text-xs text-foreground-muted">Display name</p><p className="mt-1">{selectedUser.displayName || '—'}</p></div><div><p className="text-xs text-foreground-muted">Email</p><p className="mt-1 break-all">{selectedUser.email}</p></div><div><p className="text-xs text-foreground-muted">User ID</p><p className="mt-1 break-all font-mono text-xs">{selectedUser.id}</p></div></CardContent></Card><Card><CardHeader><CardTitle className="text-sm">Tenant</CardTitle></CardHeader><CardContent className="space-y-3 text-sm"><div><p className="text-xs text-foreground-muted">Tenant</p><p className="mt-1">{selectedUser.tenantName}</p></div><div><p className="text-xs text-foreground-muted">Tenant slug</p><p className="mt-1 font-mono text-xs">{selectedUser.tenantSlug}</p></div><div><p className="text-xs text-foreground-muted">Roles</p><div className="mt-1 flex flex-wrap gap-1">{selectedUser.roles.map((role) => <Badge key={role} variant="outline">{role}</Badge>)}</div></div></CardContent></Card><Card><CardHeader><CardTitle className="text-sm">Status</CardTitle></CardHeader><CardContent className="grid grid-cols-2 gap-3 text-sm"><div><p className="text-xs text-foreground-muted">Account</p><p className="mt-1">{selectedUser.enabled ? 'Active' : 'Blocked'}</p></div><div><p className="text-xs text-foreground-muted">Email</p><p className="mt-1">{selectedUser.emailVerified ? 'Verified' : 'Unverified'}</p></div><div><p className="text-xs text-foreground-muted">Created</p><p className="mt-1">{formatDate(selectedUser.createdAt)}</p></div><div><p className="text-xs text-foreground-muted">Last active</p><p className="mt-1">{formatDate(selectedUser.lastLoginAt)}</p></div></CardContent></Card>{actionError ? <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">{actionError}</p> : null}<div className="flex flex-col gap-2 border-t border-border pt-4"><Button variant="outline" onClick={() => void handleToggleVerification(selectedUser.id, selectedUser.emailVerified)} disabled={isWorking}>{selectedUser.emailVerified ? <MailX className="size-4" /> : <Check className="size-4" />}{selectedUser.emailVerified ? 'Mark unverified' : 'Verify email'}</Button><Button variant="outline" onClick={() => void handleToggleBlocked(selectedUser.id, selectedUser.enabled)} disabled={isWorking || isCurrentUser}><UserX className="size-4" />{selectedUser.enabled ? 'Block user' : 'Unblock user'}</Button><Button variant="destructive" onClick={() => void handleDeleteUser(selectedUser.id, selectedUser.email)} disabled={isWorking || isCurrentUser}><Trash2 className="size-4" />Disable user</Button></div></div> : <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">Unable to load user details.</p>}
        </SheetContent>
      </Sheet>
    </div>
  )
}
