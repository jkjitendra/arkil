import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Check, Copy, ExternalLink } from 'lucide-react'
import { useState } from 'react'
import type { OidcConfig } from '@/lib/api'

interface OidcConfigCardProps {
  config: OidcConfig
  projectName: string
}

function CopyableField({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-muted-foreground w-40 shrink-0">{label}</span>
      <code className="text-xs bg-muted px-2 py-1.5 rounded font-mono flex-1 truncate">
        {value}
      </code>
      <Button variant="ghost" size="icon" className="h-7 w-7 shrink-0" onClick={handleCopy}>
        {copied ? <Check className="h-3 w-3 text-success" /> : <Copy className="h-3 w-3" />}
      </Button>
    </div>
  )
}

export function OidcConfigCard({ config, projectName }: OidcConfigCardProps) {
  const [snippetCopied, setSnippetCopied] = useState(false)

  const codeSnippet = `import { UserManager } from 'oidc-client-ts';

const userManager = new UserManager({
  authority: '${config.issuerUrl}',
  client_id: '${config.clientId}',
  redirect_uri: window.location.origin + '/callback',
  scope: 'openid profile email',
});`

  const handleCopySnippet = () => {
    navigator.clipboard.writeText(codeSnippet)
    setSnippetCopied(true)
    setTimeout(() => setSnippetCopied(false), 2000)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ExternalLink className="h-5 w-5" />
          OIDC Configuration
        </CardTitle>
        <CardDescription>
          Use these endpoints to integrate {projectName} with your application via OpenID Connect
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-3">
          <CopyableField label="Client ID" value={config.clientId} />
          <CopyableField label="Issuer URL" value={config.issuerUrl} />
          <CopyableField label="Authorization" value={config.authorizationEndpoint} />
          <CopyableField label="Token" value={config.tokenEndpoint} />
          <CopyableField label="JWKS" value={config.jwksUri} />
          <CopyableField label="UserInfo" value={config.userinfoEndpoint} />
        </div>

        {/* Quick-start snippet */}
        <div className="mt-6">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-medium">Quick Start (oidc-client-ts)</span>
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={handleCopySnippet}>
              {snippetCopied ? (
                <>
                  <Check className="h-3 w-3 text-success" />
                  Copied
                </>
              ) : (
                <>
                  <Copy className="h-3 w-3" />
                  Copy
                </>
              )}
            </Button>
          </div>
          <pre className="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">
            <code>{codeSnippet}</code>
          </pre>
        </div>
      </CardContent>
    </Card>
  )
}
