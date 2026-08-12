import { useEffect, useState } from 'react'
import { AlertTriangle, Building2, Check, Eye, EyeOff, KeyRound, Loader2, Monitor, Save, Shield, Smartphone, Sun, Moon, Trash2, User } from 'lucide-react'
import { useAuth } from '@/lib/auth'
import { useApiClient } from '@/lib/api'
import { useCreatePasskey, useEnrollTotp, usePasskeys, useRemovePasskey, useRemoveTotp, useRenamePasskey, useTotpStatus, useVerifyTotp } from '@/hooks/useFactors'
import { useChangePassword, useDeleteAccount, useProfile, useTenantInfo, useUpdateProfile } from '@/hooks/useProfile'
import { useTheme } from '@/theme/ThemeContext'
import { toast } from 'sonner'
import type { ThemePreference } from '@/theme/theme'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/ui/empty-state'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

type Section = 'profile' | 'security' | 'passkeys' | 'authenticator' | 'organization' | 'appearance' | 'danger'

const SECTIONS: Array<{ id: Section; label: string; icon: typeof User }> = [
  { id: 'profile', label: 'Profile', icon: User }, { id: 'security', label: 'Security', icon: Shield }, { id: 'passkeys', label: 'Passkeys', icon: KeyRound }, { id: 'authenticator', label: 'Authenticator', icon: Smartphone }, { id: 'organization', label: 'Organization', icon: Building2 }, { id: 'appearance', label: 'Appearance', icon: Monitor }, { id: 'danger', label: 'Danger zone', icon: Trash2 },
]

function PasswordField({ label, value, onChange, placeholder, id }: { label: string; value: string; onChange: (value: string) => void; placeholder: string; id: string }) {
  const [visible, setVisible] = useState(false)
  return <div><label htmlFor={id} className="text-sm font-medium text-foreground">{label}</label><div className="relative mt-1.5"><Input id={id} type={visible ? 'text' : 'password'} value={value} onChange={(event) => onChange(event.target.value)} className="pr-10" placeholder={placeholder} /><button type="button" onClick={() => setVisible((current) => !current)} className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-foreground-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background" aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}>{visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}</button></div></div>
}

function SettingsSkeleton() {
  return <div className="space-y-6"><Skeleton className="h-7 w-32" /><div className="grid gap-6 lg:grid-cols-[200px_minmax(0,1fr)]"><Skeleton className="hidden h-72 lg:block" /><Card><CardContent className="space-y-4 py-5"><Skeleton className="h-5 w-40" /><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></CardContent></Card></div></div>
}

function SettingsSectionSelect({ value, onValueChange }: { value: Section; onValueChange: (section: Section) => void }) {
  return (
    <Select value={value} onValueChange={(section) => onValueChange(section as Section)}>
      <SelectTrigger id="settings-section"><SelectValue /></SelectTrigger>
      <SelectContent>
        {SECTIONS.map((section) => <SelectItem key={section.id} value={section.id}>{section.label}</SelectItem>)}
      </SelectContent>
    </Select>
  )
}

export function SettingsPage() {
  const { data: profile, isLoading } = useProfile()
  const { data: tenant } = useTenantInfo()
  const updateProfile = useUpdateProfile()
  const changePassword = useChangePassword()
  const deleteAccount = useDeleteAccount()
  const totpStatus = useTotpStatus()
  const enrollTotp = useEnrollTotp()
  const verifyTotp = useVerifyTotp()
  const removeTotp = useRemoveTotp()
  const passkeys = usePasskeys()
  const createPasskey = useCreatePasskey()
  const renamePasskey = useRenamePasskey()
  const removePasskey = useRemovePasskey()
  const { logout } = useAuth()
  const api = useApiClient()
  const { theme, setTheme } = useTheme()

  const [activeSection, setActiveSection] = useState<Section>('profile')
  const [displayNameOverride, setDisplayNameOverride] = useState<string | null>(null)
  const displayName = displayNameOverride ?? profile?.displayName ?? ''
  const [profileSaved, setProfileSaved] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [passwordSuccess, setPasswordSuccess] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const [totpError, setTotpError] = useState('')
  const [totpSuccess, setTotpSuccess] = useState('')
  const [enrollment, setEnrollment] = useState<{ secret: string; qrCodeUri: string; digits: number; period: number } | null>(null)
  const [passkeyLabel, setPasskeyLabel] = useState('')
  const [passkeyError, setPasskeyError] = useState('')
  const [passkeySuccess, setPasskeySuccess] = useState('')
  const [editingPasskeyId, setEditingPasskeyId] = useState<string | null>(null)
  const [editingPasskeyLabel, setEditingPasskeyLabel] = useState('')
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  useEffect(() => {
    const openProfile = () => setActiveSection('profile')
    window.addEventListener('arkil:open-settings-profile', openProfile)
    return () => window.removeEventListener('arkil:open-settings-profile', openProfile)
  }, [])

  const handleSaveProfile = async () => { setProfileSaved(false); try { await updateProfile.mutateAsync({ displayName }); setProfileSaved(true); toast.success('Profile updated'); setTimeout(() => setProfileSaved(false), 3000) } catch (error) { toast.error(error instanceof Error ? error.message : 'Failed to update profile') } }
  const handleChangePassword = async () => { setPasswordError(''); setPasswordSuccess(''); if (newPassword.length < 8) { setPasswordError('Password must be at least 8 characters'); return } if (newPassword !== confirmPassword) { setPasswordError('Passwords do not match'); return } try { await changePassword.mutateAsync({ currentPassword: profile?.hasPassword ? currentPassword : undefined, newPassword }); setPasswordSuccess(profile?.hasPassword ? 'Password changed successfully' : 'Password set successfully'); toast.success('Password changed'); setCurrentPassword(''); setNewPassword(''); setConfirmPassword('') } catch (error) { setPasswordError(error instanceof Error ? error.message : 'Failed to change password'); toast.error('Failed to change password') } }
  const handleDeleteAccount = async () => { try { await deleteAccount.mutateAsync(); await logout() } catch { /* Account was disabled even if logout fails. */ } }
  const handleStartTotp = async () => { setTotpError(''); setTotpSuccess(''); try { const result = await enrollTotp.mutateAsync(); setEnrollment({ secret: result.secret, qrCodeUri: result.qrCodeUri, digits: result.digits, period: result.period }); setTotpCode('') } catch (error) { setTotpError(error instanceof Error ? error.message : 'Failed to start TOTP setup') } }
  const handleVerifyTotp = async () => { setTotpError(''); setTotpSuccess(''); if (!totpCode.trim()) { setTotpError('Enter the 6-digit authenticator code'); return } try { await verifyTotp.mutateAsync(totpCode.trim()); setTotpSuccess('Authenticator app enabled'); toast.success('TOTP enabled'); setEnrollment(null); setTotpCode('') } catch (error) { setTotpError(error instanceof Error ? error.message : 'Failed to verify authenticator code'); toast.error('Failed to enable TOTP') } }
  const handleRemoveTotp = async () => { setTotpError(''); setTotpSuccess(''); if (!window.confirm('Remove authenticator app protection from your account?')) return; try { await removeTotp.mutateAsync(); setEnrollment(null); setTotpCode(''); setTotpSuccess('Authenticator app removed'); toast.success('TOTP removed') } catch (error) { setTotpError(error instanceof Error ? error.message : 'Failed to remove authenticator app'); toast.error('Failed to remove TOTP') } }
  const base64UrlToUint8Array = (value: string) => { const normalized = value.replace(/-/g, '+').replace(/_/g, '/'); const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4); return Uint8Array.from(window.atob(padded), (char) => char.charCodeAt(0)) }
  const uint8ArrayToBase64Url = (buffer: ArrayBuffer | Uint8Array) => { const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer); let binary = ''; bytes.forEach((byte) => { binary += String.fromCharCode(byte) }); return window.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '') }
  const handleCreatePasskey = async () => { setPasskeyError(''); setPasskeySuccess(''); if (!window.PublicKeyCredential || !navigator.credentials?.create) { setPasskeyError('This browser does not support passkey registration'); return } try { const options = await api.getPasskeyRegistrationOptions(); const publicKeyOptions: PublicKeyCredentialCreationOptions = { challenge: base64UrlToUint8Array(options.challenge), rp: options.rp, user: { ...options.user, id: base64UrlToUint8Array(options.user.id) }, pubKeyCredParams: options.pubKeyCredParams.map((param) => ({ type: 'public-key' as const, alg: param.alg })), timeout: options.timeout, attestation: options.attestation as AttestationConveyancePreference, authenticatorSelection: { residentKey: options.authenticatorSelection.residentKey as ResidentKeyRequirement, userVerification: options.authenticatorSelection.userVerification as UserVerificationRequirement }, excludeCredentials: options.excludeCredentials.map((existing) => ({ type: 'public-key' as const, id: base64UrlToUint8Array(existing.id) })) }; const credential = await navigator.credentials.create({ publicKey: publicKeyOptions }); if (!credential || credential.type !== 'public-key') throw new Error('No passkey was created'); const passkey = credential as PublicKeyCredential; const response = passkey.response as AuthenticatorAttestationResponse; const publicKey = response.getPublicKey(); if (!publicKey) throw new Error('Your authenticator did not return a usable public key'); await createPasskey.mutateAsync({ flowId: options.flowId, label: passkeyLabel.trim() || undefined, credential: { id: passkey.id, rawId: uint8ArrayToBase64Url(passkey.rawId), type: passkey.type, response: { clientDataJSON: uint8ArrayToBase64Url(response.clientDataJSON), attestationObject: uint8ArrayToBase64Url(response.attestationObject), authenticatorData: uint8ArrayToBase64Url(response.getAuthenticatorData()), publicKey: uint8ArrayToBase64Url(publicKey), publicKeyAlgorithm: response.getPublicKeyAlgorithm(), transports: response.getTransports() } } }); setPasskeyLabel(''); setPasskeySuccess('Passkey added'); toast.success('Passkey added') } catch (error) { setPasskeyError(error instanceof Error ? error.message : 'Failed to register passkey'); toast.error('Failed to add passkey') } }
  const handleRenamePasskey = async (credentialId: string) => { setPasskeyError(''); setPasskeySuccess(''); if (!editingPasskeyLabel.trim()) { setPasskeyError('Passkey label is required'); return } try { await renamePasskey.mutateAsync({ credentialId, label: editingPasskeyLabel.trim() }); setEditingPasskeyId(null); setEditingPasskeyLabel(''); setPasskeySuccess('Passkey renamed'); toast.success('Passkey renamed') } catch (error) { setPasskeyError(error instanceof Error ? error.message : 'Failed to rename passkey'); toast.error('Failed to rename passkey') } }
  const handleRemovePasskey = async (credentialId: string) => { setPasskeyError(''); setPasskeySuccess(''); if (!window.confirm('Remove this passkey from your account?')) return; try { await removePasskey.mutateAsync(credentialId); setPasskeySuccess('Passkey removed'); toast.success('Passkey removed'); if (editingPasskeyId === credentialId) { setEditingPasskeyId(null); setEditingPasskeyLabel('') } } catch (error) { setPasskeyError(error instanceof Error ? error.message : 'Failed to remove passkey'); toast.error('Failed to remove passkey') } }

  if (isLoading || !profile) return <SettingsSkeleton />

  const changeSection = (section: Section) => setActiveSection(section)
  const PasswordSection = () => <Card><CardHeader><CardTitle>{profile.hasPassword ? 'Change password' : 'Set password'}</CardTitle><CardDescription>{profile.hasPassword ? 'Update the password used for email/password sign-in.' : 'Set a password for email/password sign-in.'}</CardDescription></CardHeader><CardContent className="space-y-4"><div className="max-w-xl space-y-4">{profile.hasPassword ? <PasswordField id="current-password" label="Current password" value={currentPassword} onChange={setCurrentPassword} placeholder="Enter current password" /> : null}<PasswordField id="new-password" label="New password" value={newPassword} onChange={setNewPassword} placeholder="At least 8 characters" /><PasswordField id="confirm-password" label="Confirm password" value={confirmPassword} onChange={setConfirmPassword} placeholder="Confirm new password" /></div>{passwordError ? <p className="text-sm text-danger">{passwordError}</p> : null}{passwordSuccess ? <p className="text-sm text-success">{passwordSuccess}</p> : null}<Button onClick={handleChangePassword} disabled={changePassword.isPending || !newPassword}>{changePassword.isPending ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />}{profile.hasPassword ? 'Change password' : 'Set password'}</Button></CardContent></Card>

  let content: React.ReactNode
  if (activeSection === 'profile') content = <Card><CardHeader><CardTitle>Profile</CardTitle><CardDescription>Manage your personal account information.</CardDescription></CardHeader><CardContent className="space-y-6"><div className="grid gap-4 sm:grid-cols-2"><div><label className="text-sm font-medium">Display name</label><Input value={displayName} onChange={(event) => setDisplayNameOverride(event.target.value)} className="mt-1.5" placeholder="Your display name" /></div><div><label className="text-sm font-medium">Email</label><Input value={profile.email} className="mt-1.5" readOnly /></div></div><Button onClick={handleSaveProfile} disabled={updateProfile.isPending}>{updateProfile.isPending ? <Loader2 className="size-4 animate-spin" /> : profileSaved ? <Check className="size-4" /> : <Save className="size-4" />}{profileSaved ? 'Saved' : 'Save changes'}</Button><div className="border-t border-border pt-5"><h3 className="text-sm font-medium">Connected accounts</h3>{profile.connectedAccounts.length ? <div className="mt-3 divide-y divide-border rounded-lg border border-border">{profile.connectedAccounts.map((account) => <div key={account.provider} className="flex items-center justify-between gap-3 px-3 py-2.5"><div><p className="text-sm font-medium capitalize">{account.provider}</p><p className="text-xs text-foreground-muted">{account.email}</p></div><span className="text-xs text-foreground-muted">{account.connectedAt ? `Connected ${new Date(account.connectedAt).toLocaleDateString()}` : 'Connected'}</span></div>)}</div> : <p className="mt-2 text-sm text-foreground-muted">No connected social accounts.</p>}</div></CardContent></Card>
  else if (activeSection === 'security') content = <PasswordSection />
  else if (activeSection === 'passkeys') content = <Card><CardHeader><CardTitle>Passkeys</CardTitle><CardDescription>Register device-bound credentials for passwordless sign-in.</CardDescription></CardHeader><CardContent className="space-y-5"><div className="flex flex-col gap-3 sm:flex-row sm:items-end"><div className="w-full sm:max-w-md"><label className="text-sm font-medium">New passkey label</label><Input value={passkeyLabel} onChange={(event) => setPasskeyLabel(event.target.value)} className="mt-1.5" placeholder="MacBook Pro, iPhone, Security Key" /></div><Button className="w-full shrink-0 sm:w-auto" onClick={handleCreatePasskey} disabled={createPasskey.isPending}>{createPasskey.isPending ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />}Add passkey</Button></div>{passkeys.isLoading ? <Skeleton className="h-20 w-full" /> : passkeys.data?.passkeys.length ? <div className="divide-y divide-border overflow-hidden rounded-lg border border-border">{passkeys.data.passkeys.map((passkey) => <div key={passkey.credentialId} className="space-y-3 p-3"><div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><p className="text-sm font-medium">{passkey.label}</p><p className="mt-1 text-xs text-foreground-muted">Registered {new Date(passkey.createdAt).toLocaleDateString()}{passkey.lastUsedAt ? ` · Last used ${new Date(passkey.lastUsedAt).toLocaleDateString()}` : ''}</p></div><div className="flex gap-2"><Button variant="outline" size="sm" onClick={() => { setEditingPasskeyId(passkey.credentialId); setEditingPasskeyLabel(passkey.label) }}>Rename</Button><Button variant="outline" size="sm" className="text-danger hover:text-danger" onClick={() => void handleRemovePasskey(passkey.credentialId)} disabled={removePasskey.isPending}>Remove</Button></div></div>{editingPasskeyId === passkey.credentialId ? <div className="flex gap-2"><Input value={editingPasskeyLabel} onChange={(event) => setEditingPasskeyLabel(event.target.value)} /><Button size="sm" onClick={() => void handleRenamePasskey(passkey.credentialId)} disabled={renamePasskey.isPending}>Save</Button><Button variant="outline" size="sm" onClick={() => { setEditingPasskeyId(null); setEditingPasskeyLabel('') }}>Cancel</Button></div> : null}</div>)}</div> : <EmptyState icon={KeyRound} title="No passkeys registered" description="Add a passkey to sign in securely without a password." className="min-h-48" />}{passkeyError ? <p className="text-sm text-danger">{passkeyError}</p> : null}{passkeySuccess ? <p className="text-sm text-success">{passkeySuccess}</p> : null}</CardContent></Card>
  else if (activeSection === 'authenticator') content = <Card><CardHeader><CardTitle>Authenticator app</CardTitle><CardDescription>Add a TOTP challenge to password sign-in.</CardDescription></CardHeader><CardContent className="space-y-4"><div className="flex flex-col items-start gap-3 rounded-lg border border-border p-4"><div><p className="text-sm font-medium">{totpStatus.data?.enabled ? 'Enabled' : enrollment ? 'Setup in progress' : 'Not enabled'}</p><p className="mt-1 text-sm text-foreground-secondary">{totpStatus.data?.enabled ? 'You will be prompted for a 6-digit code on password sign-in.' : 'Use Google Authenticator, 1Password, or another TOTP app.'}</p></div>{totpStatus.isLoading ? <Loader2 className="size-4 animate-spin" /> : totpStatus.data?.enabled ? <Button variant="outline" onClick={() => void handleRemoveTotp()} disabled={removeTotp.isPending}>Remove</Button> : <Button onClick={() => void handleStartTotp()} disabled={enrollTotp.isPending}>Set up</Button>}</div>{enrollment && !totpStatus.data?.enabled ? <div className="space-y-4 rounded-lg border border-border p-4"><div><p className="text-sm font-medium">Setup secret</p><code className="mt-2 block break-all rounded-lg bg-surface-raised px-3 py-2 font-mono text-xs">{enrollment.secret}</code></div><div><p className="text-sm font-medium">Provisioning URI</p><code className="mt-2 block break-all rounded-lg bg-surface-raised px-3 py-2 font-mono text-xs">{enrollment.qrCodeUri}</code><p className="mt-1 text-xs text-foreground-muted">Digits: {enrollment.digits} · Period: {enrollment.period}s</p></div><div className="flex max-w-sm gap-2"><Input value={totpCode} onChange={(event) => setTotpCode(event.target.value)} placeholder="123456" inputMode="numeric" maxLength={6} /><Button onClick={() => void handleVerifyTotp()} disabled={verifyTotp.isPending}>Confirm</Button></div></div> : null}{totpError ? <p className="text-sm text-danger">{totpError}</p> : null}{totpSuccess ? <p className="text-sm text-success">{totpSuccess}</p> : null}</CardContent></Card>
  else if (activeSection === 'organization') content = <Card><CardHeader><CardTitle>Organization</CardTitle><CardDescription>Read-only workspace and tenant identifiers.</CardDescription></CardHeader><CardContent className="grid gap-5 sm:grid-cols-2"><div><p className="text-xs font-medium text-foreground-muted">Name</p><p className="mt-1 text-sm text-foreground">{tenant?.name || profile.tenant.name}</p></div><div><p className="text-xs font-medium text-foreground-muted">Slug</p><p className="mt-1 font-mono text-sm text-foreground">{tenant?.slug || profile.tenant.slug}</p></div><div><p className="text-xs font-medium text-foreground-muted">Tenant ID</p><p className="mt-1 break-all font-mono text-sm text-foreground">{tenant?.id || profile.tenant.id}</p></div><div><p className="text-xs font-medium text-foreground-muted">Status</p><p className="mt-1 text-sm text-foreground">{tenant?.enabled === false ? 'Disabled' : 'Active'}</p></div><div><p className="text-xs font-medium text-foreground-muted">Roles</p><div className="mt-1.5 flex flex-wrap gap-1">{profile.roles.map((role) => <Badge key={role} variant="outline">{role}</Badge>)}</div></div></CardContent></Card>
  else if (activeSection === 'appearance') content = <Card><CardHeader><CardTitle>Appearance</CardTitle><CardDescription>Choose how Arkil appears on this device.</CardDescription></CardHeader><CardContent><div className="inline-flex rounded-lg border border-border bg-surface-raised p-1">{([{ id: 'light', label: 'Light', icon: Sun }, { id: 'dark', label: 'Dark', icon: Moon }, { id: 'system', label: 'System', icon: Monitor }] as Array<{ id: ThemePreference; label: string; icon: typeof Sun }>).map(({ id, label, icon: Icon }) => <button key={id} type="button" onClick={() => setTheme(id)} className={theme === id ? 'inline-flex items-center gap-2 rounded-md bg-surface px-3 py-2 text-sm font-medium text-foreground shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50' : 'inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm text-foreground-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50'}><Icon className="size-4" />{label}</button>)}</div></CardContent></Card>
  else content = <Card className="border-danger/35"><CardHeader><CardTitle className="text-danger">Danger zone</CardTitle><CardDescription>Irreversible and destructive account actions.</CardDescription></CardHeader><CardContent><div className="flex flex-col items-start gap-3 rounded-lg border border-danger/25 bg-danger/5 p-4"><div><p className="text-sm font-medium text-foreground">Delete account</p><p className="mt-1 text-sm text-foreground-secondary">Disable your account. Contact support for permanent deletion.</p></div>{showDeleteConfirm ? <div className="flex gap-2"><Button variant="outline" size="sm" onClick={() => setShowDeleteConfirm(false)}>Cancel</Button><Button variant="destructive" size="sm" onClick={() => void handleDeleteAccount()} disabled={deleteAccount.isPending}>{deleteAccount.isPending ? <Loader2 className="size-4 animate-spin" /> : <AlertTriangle className="size-4" />}Confirm</Button></div> : <Button variant="destructive" onClick={() => setShowDeleteConfirm(true)}><Trash2 className="size-4" />Delete account</Button>}</div></CardContent></Card>

  return (
    <div className="min-w-0 space-y-6">
      <PageHeader title="Settings" description="Manage your account, security, and preferences." />
      <div className="min-w-0 lg:grid lg:grid-cols-[200px_minmax(0,1fr)] lg:gap-8">
        <nav className="hidden lg:block" aria-label="Settings sections">
          <div className="sticky top-20 space-y-1">
            {SECTIONS.map(({ id, label, icon: Icon }) => (
              <button key={id} type="button" onClick={() => changeSection(id)} className={activeSection === id ? 'flex w-full items-center gap-2 rounded-lg bg-primary-subtle px-3 py-2 text-left text-sm font-medium text-foreground' : 'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-foreground-secondary hover:bg-surface-raised hover:text-foreground'}>
                <Icon className="size-4" />{label}
              </button>
            ))}
          </div>
        </nav>
        <div className="mb-5 min-w-0 lg:hidden">
          <label htmlFor="settings-section" className="sr-only">Settings section</label>
          <SettingsSectionSelect value={activeSection} onValueChange={changeSection} />
        </div>
        <div className="min-w-0">{content}</div>
      </div>
    </div>
  )
}
