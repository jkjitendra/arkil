import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Mail,
  Key,
  Smartphone,
  Loader2,
  Settings,
  Shield,
} from 'lucide-react'
import { useState, useEffect } from 'react'
import { useAuthMethods, useUpdateAuthMethods } from '@/hooks/useAuthPolicy'
import type { OAuthProviderSummary } from '@/lib/api'

// SVG icons for social providers (matching SocialLoginButtons pattern)
const GoogleIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24">
    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/>
    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
  </svg>
)

const GitHubIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0 1 12 6.844a9.59 9.59 0 0 1 2.504.337c1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.02 10.02 0 0 0 22 12.017C22 6.484 17.522 2 12 2z"/>
  </svg>
)

const AppleIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
    <path d="M17.05 20.28c-.98.95-2.05.88-3.08.4-1.09-.5-2.08-.48-3.24 0-1.44.62-2.2.44-3.06-.4C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"/>
  </svg>
)

const LinkedInIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
    <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/>
  </svg>
)

// Auth module metadata for display
const AUTH_MODULES = [
  {
    id: 'EMAIL_PASSWORD',
    label: 'Email / Password',
    description: 'Standard username and password login',
    icon: Mail,
    category: 'primary' as const,
  },
  {
    id: 'MAGIC_LINK',
    label: 'Magic Link',
    description: 'Passwordless sign-in using one-time email links',
    icon: Mail,
    category: 'primary' as const,
  },
  {
    id: 'OAUTH2_GOOGLE',
    label: 'Google',
    description: 'Sign in with Google',
    icon: () => <GoogleIcon />,
    category: 'social' as const,
    provider: 'google',
  },
  {
    id: 'OAUTH2_GITHUB',
    label: 'GitHub',
    description: 'Sign in with GitHub',
    icon: () => <GitHubIcon />,
    category: 'social' as const,
    provider: 'github',
  },
  {
    id: 'OAUTH2_APPLE',
    label: 'Apple',
    description: 'Sign in with Apple',
    icon: () => <AppleIcon />,
    category: 'social' as const,
    provider: 'apple',
  },
  {
    id: 'OAUTH2_LINKEDIN',
    label: 'LinkedIn',
    description: 'Sign in with LinkedIn',
    icon: () => <LinkedInIcon />,
    category: 'social' as const,
    provider: 'linkedin',
  },
  {
    id: 'PASSKEY',
    label: 'Passkey',
    description: 'Passwordless authentication with passkeys',
    icon: Key,
    category: 'advanced' as const,
  },
  {
    id: 'TOTP',
    label: 'Authenticator App',
    description: 'Time-based one-time password (TOTP)',
    icon: Smartphone,
    category: 'advanced' as const,
  },
] as const

interface AuthMethodsCardProps {
  projectId: string
  onConfigureProvider: (provider: string) => void
}

export function AuthMethodsCard({ projectId, onConfigureProvider }: AuthMethodsCardProps) {
  const { data: authMethods, isLoading } = useAuthMethods(projectId)
  const updateMutation = useUpdateAuthMethods(projectId)

  const [enabledModules, setEnabledModules] = useState<Set<string>>(new Set())

  // Sync state from server
  useEffect(() => {
    if (authMethods) {
      setEnabledModules(new Set(authMethods.enabledModules))
    }
  }, [authMethods])

  const configuredProviders = new Set(
    authMethods?.configuredProviders
      ?.filter((p: OAuthProviderSummary) => p.enabled)
      .map((p: OAuthProviderSummary) => p.provider) ?? []
  )

  const handleToggle = async (moduleId: string) => {
    const next = new Set(enabledModules)
    if (next.has(moduleId)) {
      next.delete(moduleId)
    } else {
      next.add(moduleId)
    }
    setEnabledModules(next)

    try {
      await updateMutation.mutateAsync([...next])
    } catch {
      // Revert on error
      setEnabledModules(new Set(authMethods?.enabledModules ?? []))
    }
  }

  if (isLoading) {
    return (
      <Card>
        <CardContent className="flex items-center justify-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-primary" />
        </CardContent>
      </Card>
    )
  }

  const primaryModules = AUTH_MODULES.filter(m => m.category === 'primary')
  const socialModules = AUTH_MODULES.filter(m => m.category === 'social')
  const advancedModules = AUTH_MODULES.filter(m => m.category === 'advanced')

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Shield className="h-5 w-5" />
          Authentication Methods
        </CardTitle>
        <CardDescription>
          Choose which authentication methods are available for your end users
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Primary auth */}
        <ModuleSection title="Primary" modules={primaryModules} enabled={enabledModules}
          configuredProviders={configuredProviders} onToggle={handleToggle} onConfigure={onConfigureProvider}
          isPending={updateMutation.isPending} />

        {/* Social login */}
        <ModuleSection title="Social Login" modules={socialModules} enabled={enabledModules}
          configuredProviders={configuredProviders} onToggle={handleToggle} onConfigure={onConfigureProvider}
          isPending={updateMutation.isPending} />

        {/* Advanced */}
        <ModuleSection title="Advanced" modules={advancedModules} enabled={enabledModules}
          configuredProviders={configuredProviders} onToggle={handleToggle} onConfigure={onConfigureProvider}
          isPending={updateMutation.isPending} />
      </CardContent>
    </Card>
  )
}

interface ModuleSectionProps {
  title: string
  modules: typeof AUTH_MODULES[number][]
  enabled: Set<string>
  configuredProviders: Set<string>
  onToggle: (moduleId: string) => void
  onConfigure: (provider: string) => void
  isPending: boolean
}

function ModuleSection({ title, modules, enabled, configuredProviders, onToggle, onConfigure, isPending }: ModuleSectionProps) {
  return (
    <div>
      <h3 className="text-sm font-medium text-muted-foreground mb-3">{title}</h3>
      <div className="space-y-2">
        {modules.map((module) => {
          const isEnabled = enabled.has(module.id)
          const isSocial = module.category === 'social'
          const providerName = 'provider' in module ? module.provider : undefined
          const isConfigured = providerName ? configuredProviders.has(providerName) : true
          const Icon = module.icon

          return (
            <div
              key={module.id}
              className={`flex items-center gap-3 p-3 rounded-lg border transition-colors ${
                isEnabled ? 'border-primary/30 bg-primary/5' : 'border-border'
              }`}
            >
              <div className="h-9 w-9 rounded-lg bg-muted flex items-center justify-center shrink-0">
                <Icon className="h-4 w-4" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">{module.label}</span>
                  {isSocial && !isConfigured && (
                    <span className="text-xs text-warning bg-warning/10 px-1.5 py-0.5 rounded">
                      Not configured
                    </span>
                  )}
                </div>
                <p className="text-xs text-muted-foreground">{module.description}</p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {isSocial && providerName && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-8 text-xs"
                    onClick={() => onConfigure(providerName)}
                  >
                    <Settings className="h-3.5 w-3.5" />
                    Configure
                  </Button>
                )}
                <button
                  type="button"
                  role="switch"
                  aria-checked={isEnabled}
                  disabled={isPending || (isSocial && !isConfigured && !isEnabled)}
                  onClick={() => onToggle(module.id)}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed ${
                    isEnabled ? 'bg-primary' : 'bg-muted-foreground/25'
                  }`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                      isEnabled ? 'translate-x-6' : 'translate-x-1'
                    }`}
                  />
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
