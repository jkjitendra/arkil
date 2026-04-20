import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Loader2, ExternalLink, Trash2 } from 'lucide-react'
import { useState, useEffect } from 'react'
import { useUpsertOAuthProvider, useDeleteOAuthProvider, useOAuthProviders } from '@/hooks/useAuthPolicy'

const PROVIDER_META: Record<string, {
  displayName: string
  docsUrl: string
  docsLabel: string
  defaultScopes: string
  clientIdLabel: string
  clientSecretLabel: string
  custom?: boolean
}> = {
  google: {
    displayName: 'Google',
    docsUrl: 'https://console.cloud.google.com/apis/credentials',
    docsLabel: 'Google Cloud Console',
    defaultScopes: 'openid,profile,email',
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client Secret',
  },
  github: {
    displayName: 'GitHub',
    docsUrl: 'https://github.com/settings/developers',
    docsLabel: 'GitHub Developer Settings',
    defaultScopes: 'read:user,user:email',
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client Secret',
  },
  apple: {
    displayName: 'Apple',
    docsUrl: 'https://developer.apple.com/account/resources/identifiers/list/serviceId',
    docsLabel: 'Apple Developer Portal',
    defaultScopes: 'openid,name,email',
    clientIdLabel: 'Services ID',
    clientSecretLabel: 'Client Secret (JWT)',
  },
  linkedin: {
    displayName: 'LinkedIn',
    docsUrl: 'https://www.linkedin.com/developers/apps',
    docsLabel: 'LinkedIn Developer Portal',
    defaultScopes: 'openid,profile,email',
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client Secret',
  },
  'custom-oidc': {
    displayName: 'Custom OIDC',
    docsUrl: 'https://openid.net/specs/openid-connect-core-1_0.html',
    docsLabel: 'OpenID Connect specification',
    defaultScopes: 'openid,profile,email',
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client Secret',
    custom: true,
  },
}

interface OAuthProviderSetupProps {
  projectId: string
  provider: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function OAuthProviderSetup({ projectId, provider, open, onOpenChange }: OAuthProviderSetupProps) {
  const { data: existingProviders } = useOAuthProviders(projectId)
  const upsertMutation = useUpsertOAuthProvider(projectId)
  const deleteMutation = useDeleteOAuthProvider(projectId)

  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')
  const [scopes, setScopes] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [issuerUri, setIssuerUri] = useState('')
  const [authorizationUri, setAuthorizationUri] = useState('')
  const [tokenUri, setTokenUri] = useState('')
  const [userInfoUri, setUserInfoUri] = useState('')
  const [jwkSetUri, setJwkSetUri] = useState('')
  const [userNameAttribute, setUserNameAttribute] = useState('')
  const [error, setError] = useState<string | null>(null)

  const meta = provider ? PROVIDER_META[provider] : null

  // Pre-fill from existing config when opening
  useEffect(() => {
    if (open && provider && existingProviders) {
      const existing = existingProviders.find(p => p.provider === provider)
      if (existing) {
        setClientId(existing.clientId)
        setClientSecret('') // Don't pre-fill secret (it's masked)
        setScopes(existing.scopes)
        setDisplayName(existing.displayName ?? '')
        setIssuerUri(existing.issuerUri ?? '')
        setAuthorizationUri(existing.authorizationUri ?? '')
        setTokenUri(existing.tokenUri ?? '')
        setUserInfoUri(existing.userInfoUri ?? '')
        setJwkSetUri(existing.jwkSetUri ?? '')
        setUserNameAttribute(existing.userNameAttribute ?? '')
      } else {
        setClientId('')
        setClientSecret('')
        setScopes(meta?.defaultScopes ?? '')
        setDisplayName('')
        setIssuerUri('')
        setAuthorizationUri('')
        setTokenUri('')
        setUserInfoUri('')
        setJwkSetUri('')
        setUserNameAttribute(provider === 'custom-oidc' ? 'sub' : '')
      }
      setError(null)
    }
  }, [open, provider, existingProviders, meta])

  const existingConfig = provider
    ? existingProviders?.find(p => p.provider === provider)
    : null

  const handleSave = async () => {
    if (!provider || !meta) return
    setError(null)

    if (!clientId.trim()) {
      setError(`${meta.clientIdLabel} is required`)
      return
    }
    if (!clientSecret.trim() && !existingConfig) {
      setError(`${meta.clientSecretLabel} is required`)
      return
    }
    if (meta.custom) {
      const requiredFields = [
        ['Issuer URL', issuerUri],
        ['Authorization URL', authorizationUri],
        ['Token URL', tokenUri],
        ['User Info URL', userInfoUri],
        ['JWK Set URL', jwkSetUri],
        ['User Name Attribute', userNameAttribute],
      ] as const

      const missing = requiredFields.filter(([, value]) => !value.trim())
      if (missing.length > 0) {
        setError(`${missing[0][0]} is required`)
        return
      }
    }

    try {
      await upsertMutation.mutateAsync({
        provider,
        displayName: displayName.trim() || undefined,
        clientId: clientId.trim(),
        clientSecret: clientSecret.trim() || undefined,
        scopes: scopes.trim() || undefined,
        issuerUri: issuerUri.trim() || undefined,
        authorizationUri: authorizationUri.trim() || undefined,
        tokenUri: tokenUri.trim() || undefined,
        userInfoUri: userInfoUri.trim() || undefined,
        jwkSetUri: jwkSetUri.trim() || undefined,
        userNameAttribute: userNameAttribute.trim() || undefined,
      })
      onOpenChange(false)
    } catch (err: any) {
      setError(err.message || 'Failed to save provider configuration')
    }
  }

  const handleDelete = async () => {
    if (!provider) return
    if (!confirm(`Remove ${meta?.displayName} configuration? This will also disable ${meta?.displayName} login for your end users.`)) {
      return
    }

    try {
      await deleteMutation.mutateAsync({ provider })
      onOpenChange(false)
    } catch (err: any) {
      setError(err.message || 'Failed to remove provider')
    }
  }

  if (!provider || !meta) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Configure {meta.displayName}</DialogTitle>
          <DialogDescription>
            Enter your {meta.displayName} OAuth application credentials.
            These are used when your end users sign in with {meta.displayName}.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Docs link */}
          <a
            href={meta.docsUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 text-sm text-primary hover:underline"
          >
            <ExternalLink className="h-3.5 w-3.5" />
            Get credentials from {meta.docsLabel}
          </a>

          {/* Client ID */}
          <div>
            <label className="text-sm font-medium">{meta.clientIdLabel}</label>
            <Input
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              placeholder={`Enter your ${meta.displayName} ${meta.clientIdLabel}`}
              className="mt-1.5 font-mono text-sm"
            />
          </div>

          {meta.custom && (
            <div>
              <label className="text-sm font-medium">Button Label</label>
              <Input
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Sign in with Acme SSO"
                className="mt-1.5"
              />
              <p className="text-xs text-muted-foreground mt-1">
                Optional label shown on the hosted login page.
              </p>
            </div>
          )}

          {/* Client Secret */}
          <div>
            <label className="text-sm font-medium">{meta.clientSecretLabel}</label>
            <Input
              type="password"
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              placeholder={existingConfig ? 'Leave blank to keep existing' : `Enter your ${meta.clientSecretLabel}`}
              className="mt-1.5 font-mono text-sm"
            />
            {existingConfig && (
              <p className="text-xs text-muted-foreground mt-1">
                Current: {existingConfig.clientSecretMasked}
              </p>
            )}
          </div>

          {/* Scopes */}
          <div>
            <label className="text-sm font-medium">Scopes</label>
            <Input
              value={scopes}
              onChange={(e) => setScopes(e.target.value)}
              placeholder={meta.defaultScopes}
              className="mt-1.5 font-mono text-sm"
            />
            <p className="text-xs text-muted-foreground mt-1">
              Comma-separated. Defaults: {meta.defaultScopes}
            </p>
          </div>

          {meta.custom && (
            <>
              <div>
                <label className="text-sm font-medium">Issuer URL</label>
                <Input
                  value={issuerUri}
                  onChange={(e) => setIssuerUri(e.target.value)}
                  placeholder="https://id.example.com"
                  className="mt-1.5 font-mono text-sm"
                />
              </div>
              <div>
                <label className="text-sm font-medium">Authorization URL</label>
                <Input
                  value={authorizationUri}
                  onChange={(e) => setAuthorizationUri(e.target.value)}
                  placeholder="https://id.example.com/oauth2/authorize"
                  className="mt-1.5 font-mono text-sm"
                />
              </div>
              <div>
                <label className="text-sm font-medium">Token URL</label>
                <Input
                  value={tokenUri}
                  onChange={(e) => setTokenUri(e.target.value)}
                  placeholder="https://id.example.com/oauth2/token"
                  className="mt-1.5 font-mono text-sm"
                />
              </div>
              <div>
                <label className="text-sm font-medium">User Info URL</label>
                <Input
                  value={userInfoUri}
                  onChange={(e) => setUserInfoUri(e.target.value)}
                  placeholder="https://id.example.com/userinfo"
                  className="mt-1.5 font-mono text-sm"
                />
              </div>
              <div>
                <label className="text-sm font-medium">JWK Set URL</label>
                <Input
                  value={jwkSetUri}
                  onChange={(e) => setJwkSetUri(e.target.value)}
                  placeholder="https://id.example.com/oauth2/jwks"
                  className="mt-1.5 font-mono text-sm"
                />
              </div>
              <div>
                <label className="text-sm font-medium">User Name Attribute</label>
                <Input
                  value={userNameAttribute}
                  onChange={(e) => setUserNameAttribute(e.target.value)}
                  placeholder="sub"
                  className="mt-1.5 font-mono text-sm"
                />
              </div>
            </>
          )}

          {/* Error */}
          {error && (
            <p className="text-sm text-destructive">{error}</p>
          )}
        </div>

        <DialogFooter className="flex-row justify-between sm:justify-between">
          <div>
            {existingConfig && (
              <Button
                variant="outline"
                className="text-destructive hover:text-destructive"
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Trash2 className="h-4 w-4" />
                )}
                Remove
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={upsertMutation.isPending}>
              {upsertMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              {existingConfig ? 'Update' : 'Save'}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
