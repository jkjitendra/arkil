import { Copy, ExternalLink } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { OidcConfig } from '@/lib/api'

interface OidcConfigCardProps {
  config: OidcConfig
  projectName: string
}

function copy(value: string) {
  void navigator.clipboard.writeText(value).then(() => toast.success('Copied to clipboard'))
}

function CopyableField({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 border-b border-border py-2.5 last:border-b-0">
      <span className="w-28 shrink-0 text-xs font-medium text-foreground-secondary sm:w-36">{label}</span>
      <code className="min-w-0 flex-1 truncate font-mono text-xs text-foreground">{value}</code>
      <Button variant="ghost" size="icon" className="size-7 shrink-0" onClick={() => copy(value)} aria-label={`Copy ${label}`}>
        <Copy className="size-3.5" />
      </Button>
    </div>
  )
}

export function OidcConfigCard({ config, projectName }: OidcConfigCardProps) {
  const codeSnippet = `import { UserManager } from 'oidc-client-ts';

const userManager = new UserManager({
  authority: '${config.issuerUrl}',
  client_id: '${config.clientId}',
  redirect_uri: window.location.origin + '/callback',
  scope: 'openid profile email',
});`

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><ExternalLink className="size-4" />OIDC configuration</CardTitle>
        <CardDescription>Endpoints for integrating {projectName} with OpenID Connect.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="rounded-lg border border-border bg-surface px-3">
          <CopyableField label="Client ID" value={config.clientId} />
          <CopyableField label="Issuer URL" value={config.issuerUrl} />
          <CopyableField label="Authorization" value={config.authorizationEndpoint} />
          <CopyableField label="Token" value={config.tokenEndpoint} />
          <CopyableField label="JWKS" value={config.jwksUri} />
          <CopyableField label="UserInfo" value={config.userinfoEndpoint} />
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between gap-3">
            <p className="text-sm font-medium text-foreground">Quick start · oidc-client-ts</p>
            <Button variant="outline" size="sm" onClick={() => copy(codeSnippet)}><Copy className="size-3.5" />Copy</Button>
          </div>
          <pre className="overflow-x-auto rounded-lg bg-[#111113] p-4 font-mono text-xs leading-5 text-slate-200"><code>{codeSnippet}</code></pre>
        </div>
      </CardContent>
    </Card>
  )
}
