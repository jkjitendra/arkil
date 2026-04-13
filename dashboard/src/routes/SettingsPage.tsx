import { useState, useEffect } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Save, User, Shield, Key, Loader2, Check, AlertTriangle, Smartphone } from 'lucide-react'
import { useProfile, useUpdateProfile, useChangePassword, useDeleteAccount } from '@/hooks/useProfile'
import { useEnrollTotp, useRemoveTotp, useTotpStatus, useVerifyTotp } from '@/hooks/useFactors'
import { useAuth } from '@/lib/auth'

export function SettingsPage() {
  const { data: profile, isLoading } = useProfile()
  const updateProfile = useUpdateProfile()
  const changePassword = useChangePassword()
  const deleteAccount = useDeleteAccount()
  const totpStatus = useTotpStatus()
  const enrollTotp = useEnrollTotp()
  const verifyTotp = useVerifyTotp()
  const removeTotp = useRemoveTotp()
  const { logout } = useAuth()

  // Profile form state
  const [displayName, setDisplayName] = useState('')
  const [profileSaved, setProfileSaved] = useState(false)

  // Password form state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [passwordSuccess, setPasswordSuccess] = useState('')

  // TOTP form state
  const [totpCode, setTotpCode] = useState('')
  const [totpError, setTotpError] = useState('')
  const [totpSuccess, setTotpSuccess] = useState('')
  const [enrollment, setEnrollment] = useState<{
    secret: string
    qrCodeUri: string
    digits: number
    period: number
  } | null>(null)

  // Delete confirmation
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  useEffect(() => {
    if (profile) {
      setDisplayName(profile.displayName || '')
    }
  }, [profile])

  const handleSaveProfile = async () => {
    setProfileSaved(false)
    await updateProfile.mutateAsync({ displayName })
    setProfileSaved(true)
    setTimeout(() => setProfileSaved(false), 3000)
  }

  const handleChangePassword = async () => {
    setPasswordError('')
    setPasswordSuccess('')

    if (newPassword.length < 8) {
      setPasswordError('Password must be at least 8 characters')
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('Passwords do not match')
      return
    }

    try {
      await changePassword.mutateAsync({
        currentPassword: profile?.hasPassword ? currentPassword : undefined,
        newPassword,
      })
      setPasswordSuccess(profile?.hasPassword ? 'Password changed successfully' : 'Password set successfully')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to change password'
      setPasswordError(message)
    }
  }

  const handleDeleteAccount = async () => {
    try {
      await deleteAccount.mutateAsync()
      await logout()
    } catch {
      // Account was disabled even if logout fails
    }
  }

  const handleStartTotp = async () => {
    setTotpError('')
    setTotpSuccess('')

    try {
      const result = await enrollTotp.mutateAsync()
      setEnrollment({
        secret: result.secret,
        qrCodeUri: result.qrCodeUri,
        digits: result.digits,
        period: result.period,
      })
      setTotpCode('')
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to start TOTP setup'
      setTotpError(message)
    }
  }

  const handleVerifyTotp = async () => {
    setTotpError('')
    setTotpSuccess('')

    if (!totpCode.trim()) {
      setTotpError('Enter the 6-digit authenticator code')
      return
    }

    try {
      await verifyTotp.mutateAsync(totpCode.trim())
      setTotpSuccess('Authenticator app enabled')
      setEnrollment(null)
      setTotpCode('')
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to verify authenticator code'
      setTotpError(message)
    }
  }

  const handleRemoveTotp = async () => {
    setTotpError('')
    setTotpSuccess('')

    if (!window.confirm('Remove authenticator app protection from your account?')) {
      return
    }

    try {
      await removeTotp.mutateAsync()
      setEnrollment(null)
      setTotpCode('')
      setTotpSuccess('Authenticator app removed')
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to remove authenticator app'
      setTotpError(message)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-muted-foreground mt-1">
          Manage your account and preferences
        </p>
      </div>

      {/* Profile */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-primary/20 flex items-center justify-center">
              <User className="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle>Profile</CardTitle>
              <CardDescription>Your personal information</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="text-sm font-medium">Display Name</label>
              <Input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="mt-1.5"
                placeholder="Your display name"
              />
            </div>
            <div>
              <label className="text-sm font-medium">Email</label>
              <Input value={profile?.email || ''} className="mt-1.5" readOnly />
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Button
              onClick={handleSaveProfile}
              disabled={updateProfile.isPending}
            >
              {updateProfile.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : profileSaved ? (
                <Check className="h-4 w-4" />
              ) : (
                <Save className="h-4 w-4" />
              )}
              {profileSaved ? 'Saved' : 'Save Changes'}
            </Button>
          </div>

          {/* Connected Accounts */}
          {profile?.connectedAccounts && profile.connectedAccounts.length > 0 && (
            <div className="pt-4 border-t">
              <p className="text-sm font-medium mb-3">Connected Accounts</p>
              <div className="space-y-2">
                {profile.connectedAccounts.map((account) => (
                  <div
                    key={account.provider}
                    className="flex items-center justify-between p-3 rounded-lg border"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center">
                        <span className="text-xs font-medium capitalize">
                          {account.provider.charAt(0).toUpperCase()}
                        </span>
                      </div>
                      <div>
                        <p className="text-sm font-medium capitalize">{account.provider}</p>
                        <p className="text-xs text-muted-foreground">{account.email}</p>
                      </div>
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {account.connectedAt
                        ? `Connected ${new Date(account.connectedAt).toLocaleDateString()}`
                        : 'Connected'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Account Info */}
          <div className="pt-4 border-t">
            <div className="grid gap-2 text-sm text-muted-foreground">
              <div className="flex justify-between">
                <span>Organization</span>
                <span className="font-medium text-foreground">{profile?.tenant.name}</span>
              </div>
              <div className="flex justify-between">
                <span>Roles</span>
                <span className="font-medium text-foreground">{profile?.roles.join(', ')}</span>
              </div>
              {profile?.createdAt && (
                <div className="flex justify-between">
                  <span>Member since</span>
                  <span className="font-medium text-foreground">
                    {new Date(profile.createdAt).toLocaleDateString()}
                  </span>
                </div>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Security - Password */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-primary/20 flex items-center justify-center">
              <Shield className="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle>{profile?.hasPassword ? 'Change Password' : 'Set Password'}</CardTitle>
              <CardDescription>
                {profile?.hasPassword
                  ? 'Update your password'
                  : 'Set a password for email/password login'}
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {profile?.hasPassword && (
            <div>
              <label className="text-sm font-medium">Current Password</label>
              <Input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="mt-1.5 max-w-md"
                placeholder="Enter current password"
              />
            </div>
          )}
          <div className="grid gap-4 md:grid-cols-2 max-w-2xl">
            <div>
              <label className="text-sm font-medium">New Password</label>
              <Input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="mt-1.5"
                placeholder="At least 8 characters"
              />
            </div>
            <div>
              <label className="text-sm font-medium">Confirm Password</label>
              <Input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="mt-1.5"
                placeholder="Confirm new password"
              />
            </div>
          </div>

          {passwordError && (
            <p className="text-sm text-destructive">{passwordError}</p>
          )}
          {passwordSuccess && (
            <p className="text-sm text-green-600">{passwordSuccess}</p>
          )}

          <Button
            onClick={handleChangePassword}
            disabled={changePassword.isPending || !newPassword}
          >
            {changePassword.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Key className="h-4 w-4" />
            )}
            {profile?.hasPassword ? 'Change Password' : 'Set Password'}
          </Button>
        </CardContent>
      </Card>

      {/* Security - TOTP */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-primary/20 flex items-center justify-center">
              <Smartphone className="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle>Authenticator App</CardTitle>
              <CardDescription>
                Add a TOTP code challenge to password sign-in
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between rounded-lg border p-4">
            <div>
              <p className="font-medium">
                {totpStatus.data?.enabled ? 'Enabled' : enrollment ? 'Setup in progress' : 'Not enabled'}
              </p>
              <p className="text-sm text-muted-foreground">
                {totpStatus.data?.enabled
                  ? 'You will be prompted for a 6-digit code on password sign-in.'
                  : 'Use Google Authenticator, 1Password, or another TOTP app.'}
              </p>
            </div>
            {totpStatus.isLoading ? (
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            ) : totpStatus.data?.enabled ? (
              <Button variant="outline" onClick={handleRemoveTotp} disabled={removeTotp.isPending}>
                {removeTotp.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                Remove
              </Button>
            ) : (
              <Button onClick={handleStartTotp} disabled={enrollTotp.isPending}>
                {enrollTotp.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                Set Up
              </Button>
            )}
          </div>

          {enrollment && !totpStatus.data?.enabled && (
            <div className="space-y-4 rounded-lg border p-4">
              <div className="space-y-2">
                <p className="text-sm font-medium">Setup Secret</p>
                <code className="block rounded bg-muted px-3 py-2 text-sm break-all">
                  {enrollment.secret}
                </code>
                <p className="text-xs text-muted-foreground">
                  Add this secret to your authenticator app if QR scanning is unavailable.
                </p>
              </div>

              <div className="space-y-2">
                <p className="text-sm font-medium">Provisioning URI</p>
                <code className="block rounded bg-muted px-3 py-2 text-xs break-all">
                  {enrollment.qrCodeUri}
                </code>
                <p className="text-xs text-muted-foreground">
                  Digits: {enrollment.digits} · Period: {enrollment.period}s
                </p>
              </div>

              <div className="grid gap-3 md:grid-cols-[minmax(0,260px)_auto] md:items-end">
                <div>
                  <label className="text-sm font-medium">Verify Code</label>
                  <Input
                    value={totpCode}
                    onChange={(e) => setTotpCode(e.target.value)}
                    className="mt-1.5"
                    placeholder="123456"
                    inputMode="numeric"
                    maxLength={6}
                  />
                </div>
                <Button onClick={handleVerifyTotp} disabled={verifyTotp.isPending}>
                  {verifyTotp.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  Confirm
                </Button>
              </div>
            </div>
          )}

          {totpError && (
            <p className="text-sm text-destructive">{totpError}</p>
          )}
          {totpSuccess && (
            <p className="text-sm text-green-600">{totpSuccess}</p>
          )}
        </CardContent>
      </Card>

      {/* Danger Zone */}
      <Card className="border-destructive/50">
        <CardHeader>
          <CardTitle className="text-destructive">Danger Zone</CardTitle>
          <CardDescription>Irreversible and destructive actions</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between p-4 rounded-lg border border-destructive/20 bg-destructive/5">
            <div>
              <p className="font-medium">Delete Account</p>
              <p className="text-sm text-muted-foreground">
                Disable your account. Contact support for permanent deletion.
              </p>
            </div>
            {showDeleteConfirm ? (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setShowDeleteConfirm(false)}
                >
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleDeleteAccount}
                  disabled={deleteAccount.isPending}
                >
                  {deleteAccount.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <AlertTriangle className="h-4 w-4" />
                  )}
                  Confirm
                </Button>
              </div>
            ) : (
              <Button
                variant="destructive"
                onClick={() => setShowDeleteConfirm(true)}
              >
                Delete Account
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
