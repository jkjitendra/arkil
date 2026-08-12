import { Building2, KeyRound, Mail, Settings2, ShieldCheck, Smartphone } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { useAuthMethods, useUpdateAuthMethods } from '@/hooks/useAuthPolicy'
import type { OAuthProviderSummary } from '@/lib/api'

const GoogleIcon = () => <svg className="size-4" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
const GitHubIcon = () => <svg className="size-4" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0 1 12 6.844a9.59 9.59 0 0 1 2.504.337c1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.02 10.02 0 0 0 22 12.017C22 6.484 17.522 2 12 2z"/></svg>

const AUTH_MODULES = [
  { id: 'EMAIL_PASSWORD', label: 'Email / Password', description: 'Standard email and password sign-in', icon: Mail, category: 'primary' },
  { id: 'MAGIC_LINK', label: 'Magic Link', description: 'Passwordless sign-in using one-time links', icon: Mail, category: 'primary' },
  { id: 'OAUTH2_GOOGLE', label: 'Google', description: 'Sign in with Google', icon: GoogleIcon, category: 'social', provider: 'google' },
  { id: 'OAUTH2_GITHUB', label: 'GitHub', description: 'Sign in with GitHub', icon: GitHubIcon, category: 'social', provider: 'github' },
  { id: 'OAUTH2_APPLE', label: 'Apple', description: 'Sign in with Apple', icon: Building2, category: 'social', provider: 'apple' },
  { id: 'OAUTH2_LINKEDIN', label: 'LinkedIn', description: 'Sign in with LinkedIn', icon: Building2, category: 'social', provider: 'linkedin' },
  { id: 'OAUTH2_CUSTOM_OIDC', label: 'Enterprise SSO', description: 'Connect an OpenID Connect identity provider', icon: Building2, category: 'social', provider: 'custom-oidc' },
  { id: 'PASSKEY', label: 'Passkey', description: 'Passwordless authentication with passkeys', icon: KeyRound, category: 'advanced' },
  { id: 'TOTP', label: 'Authenticator App', description: 'Time-based one-time password verification', icon: Smartphone, category: 'advanced' },
] as const

interface AuthMethodsCardProps { projectId: string; onConfigureProvider: (provider: string) => void }

export function AuthMethodsCard({ projectId, onConfigureProvider }: AuthMethodsCardProps) {
  const { data: authMethods, isLoading } = useAuthMethods(projectId)
  const updateMutation = useUpdateAuthMethods(projectId)
  const [optimisticModules, setOptimisticModules] = useState<Set<string> | null>(null)
  const enabledModules = optimisticModules ?? new Set(authMethods?.enabledModules ?? [])
  const configuredProviders = new Set(authMethods?.configuredProviders?.filter((provider: OAuthProviderSummary) => provider.enabled).map((provider: OAuthProviderSummary) => provider.provider) ?? [])

  const handleToggle = async (moduleId: string) => {
    const next = new Set(enabledModules)
    if (next.has(moduleId)) {
      next.delete(moduleId)
    } else {
      next.add(moduleId)
    }
    setOptimisticModules(next)
    try {
      await updateMutation.mutateAsync([...next])
    } finally {
      setOptimisticModules(null)
    }
  }

  if (isLoading) return <Card><CardContent className="space-y-3 py-5"><Skeleton className="h-16 w-full" /><Skeleton className="h-16 w-full" /><Skeleton className="h-16 w-full" /></CardContent></Card>

  return (
    <Card>
      <CardHeader><CardTitle className="flex items-center gap-2"><ShieldCheck className="size-4" />Authentication methods</CardTitle><CardDescription>Control the sign-in methods available to your users.</CardDescription></CardHeader>
      <CardContent className="space-y-6">
        {(['primary', 'social', 'advanced'] as const).map((category) => (
          <section key={category}>
            <h3 className="mb-2 text-xs font-medium uppercase tracking-[0.08em] text-foreground-muted">{category === 'primary' ? 'Primary' : category === 'social' ? 'Social connections' : 'Advanced'}</h3>
            <div className="overflow-hidden rounded-lg border border-border">
              {AUTH_MODULES.filter((module) => module.category === category).map((module, index) => {
                const enabled = enabledModules.has(module.id)
                const provider = 'provider' in module ? module.provider : undefined
                const configured = provider ? configuredProviders.has(provider) : true
                const Icon = module.icon
                return (
                  <div key={module.id} className={index > 0 ? 'flex items-center gap-3 border-t border-border border-l-[3px] border-l-primary/40 px-3 py-3' : 'flex items-center gap-3 border-l-[3px] border-l-primary/40 px-3 py-3'}>
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg border border-border bg-surface-raised text-foreground-secondary"><Icon /></span>
                    <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-x-2 gap-y-1"><p className="text-sm font-medium text-foreground">{module.label}</p>{provider ? <span className={configured ? 'inline-flex items-center gap-1 text-xs text-success' : 'inline-flex items-center gap-1 text-xs text-warning'}><span className={configured ? 'size-1.5 rounded-full bg-success' : 'size-1.5 rounded-full bg-warning'} />{configured ? 'Connected' : 'Setup required'}</span> : <span className={enabled ? 'inline-flex items-center gap-1 text-xs text-primary' : 'inline-flex items-center gap-1 text-xs text-foreground-muted'}><span className={enabled ? 'size-1.5 rounded-full bg-primary' : 'size-1.5 rounded-full bg-foreground-muted'} />{enabled ? 'Enabled' : 'Disabled'}</span>}</div><p className="mt-0.5 text-xs text-foreground-secondary">{module.description}</p></div>
                    {provider ? <Button variant="ghost" size="sm" className="hidden text-foreground-secondary hover:bg-primary-subtle hover:text-primary sm:inline-flex" onClick={() => onConfigureProvider(provider)}><Settings2 className="size-3.5" />Configure</Button> : null}
                    <Switch checked={enabled} onCheckedChange={() => void handleToggle(module.id)} disabled={updateMutation.isPending || (Boolean(provider) && !configured && !enabled)} />
                  </div>
                )
              })}
            </div>
          </section>
        ))}
      </CardContent>
    </Card>
  )
}
