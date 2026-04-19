import { useDeferredValue, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
import { useAdminUser, useAdminUsers, useBlockAdminUser, useDeleteAdminUser, useUnblockAdminUser, useUpdateAdminUser } from '@/hooks/useAdminUsers'
import { useProfile } from '@/hooks/useProfile'
import { AlertCircle, Check, Eye, Loader2, MailCheck, MailX, Search, Shield, Trash2, UserX } from 'lucide-react'

const PAGE_SIZE = 12

function formatDate(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

export function UsersPage() {
  const { data: profile } = useProfile()
  const canManageUsers = !!profile?.roles.some((role) => role === 'TENANT_ADMIN' || role === 'SUPER_ADMIN' || role === 'PLATFORM_ADMIN')
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
  const filteredUsers = deferredSearch
    ? users.filter((user) =>
      [user.displayName, user.username, user.email, user.tenantName, user.tenantSlug]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(deferredSearch))
    )
    : users

  const selectedUser = userDetail.data
  const isCurrentUser = !!selectedUser && selectedUser.id === profile?.id

  const handleToggleVerification = async (userId: string, emailVerified: boolean) => {
    setActionError('')
    try {
      await updateUser.mutateAsync({
        userId,
        data: { emailVerified: !emailVerified },
      })
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update verification status')
    }
  }

  const handleToggleBlocked = async (userId: string, enabled: boolean) => {
    setActionError('')
    try {
      if (enabled) {
        await blockUser.mutateAsync({ userId, reason: 'Blocked from dashboard' })
      } else {
        await unblockUser.mutateAsync(userId)
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update account status')
    }
  }

  const handleDeleteUser = async (userId: string, email: string) => {
    setActionError('')
    if (!window.confirm(`Disable ${email}? This preserves audit history and can be reversed later.`)) {
      return
    }

    try {
      await deleteUser.mutateAsync(userId)
      setSelectedUserId(null)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to delete user')
    }
  }

  const isWorking =
    updateUser.isPending ||
    blockUser.isPending ||
    unblockUser.isPending ||
    deleteUser.isPending

  if (!profile) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!canManageUsers) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] text-center">
        <Shield className="mb-4 h-12 w-12 text-muted-foreground" />
        <h2 className="text-xl font-semibold">Users access requires an admin role</h2>
        <p className="mt-2 max-w-lg text-muted-foreground">
          Sign in with a tenant admin or super admin account to manage end users.
        </p>
      </div>
    )
  }

  if (usersQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (usersQuery.error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h2 className="text-xl font-semibold">Failed to load users</h2>
        <p className="text-muted-foreground mt-2">{usersQuery.error.message}</p>
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Users</h1>
          <p className="text-muted-foreground mt-1">
            Review end users in your tenant, verify emails, and manage account access.
          </p>
        </div>
        <div className="w-full max-w-md">
          <label className="text-sm font-medium">Search users</label>
          <div className="relative mt-1.5">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by name, username, email, or tenant"
              className="pl-9"
            />
          </div>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>User Directory</CardTitle>
          <CardDescription>
            Showing {filteredUsers.length} of {usersQuery.data?.content.length || 0} users on this page
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {filteredUsers.length === 0 ? (
            <div className="rounded-lg border border-dashed p-10 text-center text-sm text-muted-foreground">
              No users matched this page.
            </div>
          ) : (
            filteredUsers.map((user) => {
              const isSelf = user.id === profile?.id
              return (
              <div key={user.id} className="rounded-lg border p-4">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium">{user.displayName || user.username}</p>
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${user.enabled ? 'bg-success/15 text-success' : 'bg-destructive/15 text-destructive'}`}>
                        {user.enabled ? 'Active' : 'Disabled'}
                      </span>
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${user.emailVerified ? 'bg-info/15 text-info' : 'bg-warning/15 text-warning'}`}>
                        {user.emailVerified ? 'Verified' : 'Unverified'}
                      </span>
                    </div>
                    <div className="space-y-1 text-sm text-muted-foreground">
                      <p>{user.email}</p>
                      <p>@{user.username} · {user.tenantName}</p>
                      <p>{user.roles.join(', ') || 'No roles assigned'}</p>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Last login {formatDate(user.lastLoginAt)}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" onClick={() => setSelectedUserId(user.id)}>
                      <Eye className="h-4 w-4" />
                      View
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => handleToggleVerification(user.id, user.emailVerified)}
                      disabled={isWorking || isSelf}
                    >
                      {user.emailVerified ? <MailX className="h-4 w-4" /> : <MailCheck className="h-4 w-4" />}
                      {user.emailVerified ? 'Mark Unverified' : 'Verify Email'}
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => handleToggleBlocked(user.id, user.enabled)}
                      disabled={isWorking || isSelf}
                    >
                      <UserX className="h-4 w-4" />
                      {user.enabled ? 'Block' : 'Unblock'}
                    </Button>
                  </div>
                </div>
              </div>
            )})
          )}

          <div className="flex items-center justify-between border-t pt-4">
            <p className="text-sm text-muted-foreground">
              Page {page + 1} of {Math.max(usersQuery.data?.totalPages || 1, 1)}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                onClick={() => setPage((current) => current + 1)}
                disabled={!!usersQuery.data?.last}
              >
                Next
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Dialog open={!!selectedUserId} onOpenChange={(open) => !open && setSelectedUserId(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>User Details</DialogTitle>
            <DialogDescription>
              Review identity state and apply admin actions for this account.
            </DialogDescription>
          </DialogHeader>

          {userDetail.isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : selectedUser ? (
            <div className="space-y-6">
              <div className="grid gap-4 md:grid-cols-2">
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">Identity</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Display name</span>
                      <span>{selectedUser.displayName || '—'}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Username</span>
                      <span>@{selectedUser.username}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Email</span>
                      <span>{selectedUser.email}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">User ID</span>
                      <span className="font-mono text-xs">{selectedUser.id}</span>
                    </div>
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">Tenant</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2 text-sm">
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Tenant</span>
                      <span>{selectedUser.tenantName}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Slug</span>
                      <span>{selectedUser.tenantSlug}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Tenant ID</span>
                      <span className="font-mono text-xs">{selectedUser.tenantId}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-muted-foreground">Roles</span>
                      <span>{selectedUser.roles.join(', ') || '—'}</span>
                    </div>
                  </CardContent>
                </Card>
              </div>

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Status</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-2 text-sm md:grid-cols-2">
                  <div className="flex justify-between gap-4">
                    <span className="text-muted-foreground">Account state</span>
                    <span>{selectedUser.enabled ? 'Active' : 'Disabled'}</span>
                  </div>
                  <div className="flex justify-between gap-4">
                    <span className="text-muted-foreground">Email verification</span>
                    <span>{selectedUser.emailVerified ? 'Verified' : 'Unverified'}</span>
                  </div>
                  <div className="flex justify-between gap-4">
                    <span className="text-muted-foreground">Created</span>
                    <span>{formatDate(selectedUser.createdAt)}</span>
                  </div>
                  <div className="flex justify-between gap-4">
                    <span className="text-muted-foreground">Updated</span>
                    <span>{formatDate(selectedUser.updatedAt)}</span>
                  </div>
                  <div className="flex justify-between gap-4">
                    <span className="text-muted-foreground">Last login</span>
                    <span>{formatDate(selectedUser.lastLoginAt)}</span>
                  </div>
                </CardContent>
              </Card>

              {actionError && (
                <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {actionError}
                </p>
              )}
            </div>
          ) : (
            <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              Unable to load user details.
            </div>
          )}

          <DialogFooter className="flex-wrap gap-2">
            {selectedUser && (
              <>
                <Button
                  variant="outline"
                  onClick={() => handleToggleVerification(selectedUser.id, selectedUser.emailVerified)}
                  disabled={isWorking}
                >
                  {selectedUser.emailVerified ? <MailX className="h-4 w-4" /> : <Check className="h-4 w-4" />}
                  {selectedUser.emailVerified ? 'Mark Unverified' : 'Verify Email'}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => handleToggleBlocked(selectedUser.id, selectedUser.enabled)}
                  disabled={isWorking || isCurrentUser}
                >
                  <Shield className="h-4 w-4" />
                  {selectedUser.enabled ? 'Block User' : 'Unblock User'}
                </Button>
                <Button
                  variant="destructive"
                  onClick={() => handleDeleteUser(selectedUser.id, selectedUser.email)}
                  disabled={isWorking || isCurrentUser}
                >
                  <Trash2 className="h-4 w-4" />
                  Disable User
                </Button>
              </>
            )}
            <Button variant="outline" onClick={() => setSelectedUserId(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
