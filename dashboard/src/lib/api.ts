import { useAuth } from './auth'

const API_BASE = '/api/v1'

// Custom error for API failures with detailed error info
export class ApiError extends Error {
  status: number
  details?: Record<string, unknown>

  constructor(status: number, message: string, details?: Record<string, unknown>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }

  // Helper to get validation errors from details
  getValidationErrors(): string[] {
    if (!this.details) return []
    if (Array.isArray(this.details.messages)) return this.details.messages
    if (Array.isArray(this.details.errors)) {
      return this.details.errors.map((e: { message?: string }) => e.message || String(e))
    }
    return []
  }
}

// Generic fetch wrapper with auth
async function fetchWithAuth(
  path: string,
  options: RequestInit = {},
  getToken: () => Promise<string | null>
): Promise<Response> {
  const token = await getToken()

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  }

  if (token) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({ message: response.statusText }))
    throw new ApiError(
      response.status,
      errorBody.message || errorBody.error || 'Request failed',
      errorBody
    )
  }

  return response
}

// ─────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────

export interface OidcConfig {
  clientId: string
  issuerUrl: string
  authorizationEndpoint: string
  tokenEndpoint: string
  jwksUri: string
  userinfoEndpoint: string
}

export interface Project {
  id: string
  name: string
  slug: string
  description?: string
  iconUrl?: string
  environment: 'DEVELOPMENT' | 'PRODUCTION'
  allowedOrigins: string[]
  redirectUris: string[]
  ownerId: string
  createdAt: string
  updatedAt: string
  oidcConfig?: OidcConfig
}

export interface DeletedProject {
  id: string
  name: string
  slug: string
  deletedAt: string
  daysRemaining: number
}

export interface ApiKey {
  id: string
  keyId: string
  name: string
  publishableKey: string
  secretKeyMasked: string // Pre-formatted: "sk_***XXXX"
  keyType: 'TEST' | 'LIVE'
  status: 'ACTIVE' | 'ROTATING' | 'REVOKED' | 'EXPIRED'
  createdAt: string
  lastUsedAt?: string
  gracePeriodEndsAt?: string
}

export interface CreateProjectRequest {
  name: string
  slug?: string
  description?: string
  environment?: 'DEVELOPMENT' | 'PRODUCTION'
  allowedOrigins?: string[]
  redirectUris?: string[]
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
  iconUrl?: string
  environment?: 'DEVELOPMENT' | 'PRODUCTION'
  allowedOrigins?: string[]
  redirectUris?: string[]
}

export interface CreateKeyRequest {
  name: string
  keyType?: 'TEST' | 'LIVE'
}

export interface KeyPairResult {
  apiKey: ApiKey
  secretKey: string // Only available on create/rotate
}

// ─────────────────────────────────────────────────────────────────
// Auth Methods & OAuth Providers
// ─────────────────────────────────────────────────────────────────

export interface OAuthProviderSummary {
  provider: string
  clientId: string
  environment: string
  enabled: boolean
}

export interface AuthMethodsResponse {
  enabledModules: string[]
  configuredProviders: OAuthProviderSummary[]
}

export interface OAuthProviderDto {
  id: string
  provider: string
  clientId: string
  clientSecretMasked: string
  scopes: string
  environment: string
  enabled: boolean
  createdAt: string
  updatedAt?: string
}

export interface UpsertOAuthProviderRequest {
  provider: string
  clientId: string
  clientSecret?: string
  scopes?: string
  environment?: string
  enabled?: boolean
}

// ─────────────────────────────────────────────────────────────────
// Webhooks
// ─────────────────────────────────────────────────────────────────

export interface Webhook {
  id: string
  url: string
  events: string
  description?: string
  enabled: boolean
  createdAt: string
  updatedAt?: string
}

export interface WebhookCreated extends Webhook {
  signingSecret: string
}

export interface CreateWebhookRequest {
  url: string
  events: string[]
  description?: string
}

export interface UpdateWebhookRequest {
  url?: string
  events?: string[]
  description?: string
  enabled?: boolean
}

// ─────────────────────────────────────────────────────────────────
// User Profile & Tenant
// ─────────────────────────────────────────────────────────────────

export interface ConnectedAccount {
  provider: string
  email: string
  displayName: string
  pictureUrl?: string
  connectedAt?: string
}

export interface TenantInfo {
  id: string
  name: string
  slug: string
}

export interface UserProfile {
  id: string
  username: string
  email: string
  displayName?: string
  emailVerified: boolean
  enabled: boolean
  hasPassword: boolean
  connectedAccounts: ConnectedAccount[]
  tenant: TenantInfo
  roles: string[]
  createdAt?: string
  lastLoginAt?: string
}

export interface UpdateProfileRequest {
  displayName?: string
}

export interface ChangePasswordRequest {
  currentPassword?: string
  newPassword: string
}

export interface TotpStatusResponse {
  enabled: boolean
}

export interface TotpEnrollmentResponse {
  secret: string
  qrCodeUri: string
  algorithm: string
  digits: number
  period: number
  message: string
}

export interface Passkey {
  id: string
  credentialId: string
  label: string
  createdAt: string
  lastUsedAt?: string
  rpId: string
  userVerificationCapable: boolean
}

export interface PasskeyRegistrationOptions {
  flowId: string
  challenge: string
  rp: {
    name: string
    id: string
  }
  user: {
    id: string
    name: string
    displayName: string
  }
  pubKeyCredParams: Array<{
    type: string
    alg: number
  }>
  timeout: number
  attestation: string
  authenticatorSelection: {
    residentKey: string
    userVerification: string
  }
  excludeCredentials: Array<{
    type: string
    id: string
  }>
}

// ─────────────────────────────────────────────────────────────────
// Public API (no auth required)
// ─────────────────────────────────────────────────────────────────

export interface RegisterRequest {
  email: string
  password: string
  orgName: string
  displayName?: string
}

export async function registerDeveloper(data: RegisterRequest): Promise<{ message: string; userId: string; email: string; orgName: string }> {
  const response = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

  const body = await response.json()

  if (!response.ok) {
    throw new ApiError(response.status, body.message || 'Registration failed', body)
  }

  return body
}

export async function fetchPlatformProviders(): Promise<string[]> {
  const response = await fetch(`${API_BASE}/public/platform-providers`)
  if (!response.ok) return []
  return response.json()
}

// ─────────────────────────────────────────────────────────────────
// API Client Factory
// ─────────────────────────────────────────────────────────────────

export function createApiClient(getToken: () => Promise<string | null>) {
  const fetcher = (path: string, options?: RequestInit) =>
    fetchWithAuth(path, options, getToken)

  return {
    // Projects
    async listProjects(): Promise<Project[]> {
      const res = await fetcher('/projects')
      return res.json()
    },

    async getProject(id: string): Promise<Project> {
      const res = await fetcher(`/projects/${id}`)
      return res.json()
    },

    async createProject(data: CreateProjectRequest): Promise<{ project: Project; secretKey?: string }> {
      const res = await fetcher('/projects', {
        method: 'POST',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async updateProject(id: string, data: UpdateProjectRequest): Promise<Project> {
      const res = await fetcher(`/projects/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async deleteProject(id: string): Promise<void> {
      await fetcher(`/projects/${id}`, { method: 'DELETE' })
    },

    async listDeletedProjects(): Promise<DeletedProject[]> {
      const res = await fetcher('/projects/deleted')
      return res.json()
    },

    async restoreProject(id: string): Promise<Project> {
      const res = await fetcher(`/projects/${id}/restore`, { method: 'POST' })
      return res.json()
    },

    // API Keys
    async listKeys(projectId: string): Promise<ApiKey[]> {
      const res = await fetcher(`/projects/${projectId}/keys`)
      return res.json()
    },

    async createKey(projectId: string, data: CreateKeyRequest): Promise<KeyPairResult> {
      const res = await fetcher(`/projects/${projectId}/keys`, {
        method: 'POST',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async rotateKey(projectId: string, keyId: string): Promise<KeyPairResult> {
      const res = await fetcher(`/projects/${projectId}/keys/${keyId}/rotate`, {
        method: 'POST',
      })
      return res.json()
    },

    async revokeKey(projectId: string, keyId: string, reason?: string): Promise<void> {
      const params = reason ? `?reason=${encodeURIComponent(reason)}` : ''
      await fetcher(`/projects/${projectId}/keys/${keyId}${params}`, { method: 'DELETE' })
    },

    // Auth Methods
    async getAuthMethods(projectId: string): Promise<AuthMethodsResponse> {
      const res = await fetcher(`/projects/${projectId}/auth-methods`)
      return res.json()
    },

    async updateAuthMethods(projectId: string, enabledModules: string[]): Promise<{ enabledModules: string[] }> {
      const res = await fetcher(`/projects/${projectId}/auth-methods`, {
        method: 'PUT',
        body: JSON.stringify({ enabledModules }),
      })
      return res.json()
    },

    // OAuth Providers
    async listOAuthProviders(projectId: string): Promise<OAuthProviderDto[]> {
      const res = await fetcher(`/projects/${projectId}/oauth-providers`)
      return res.json()
    },

    async upsertOAuthProvider(projectId: string, data: UpsertOAuthProviderRequest): Promise<OAuthProviderDto> {
      const res = await fetcher(`/projects/${projectId}/oauth-providers`, {
        method: 'POST',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async deleteOAuthProvider(projectId: string, provider: string, environment?: string): Promise<void> {
      const params = environment ? `?environment=${encodeURIComponent(environment)}` : ''
      await fetcher(`/projects/${projectId}/oauth-providers/${provider}${params}`, { method: 'DELETE' })
    },

    // Webhooks
    async listWebhooks(projectId: string): Promise<Webhook[]> {
      const res = await fetcher(`/projects/${projectId}/webhooks`)
      return res.json()
    },

    async createWebhook(projectId: string, data: CreateWebhookRequest): Promise<WebhookCreated> {
      const res = await fetcher(`/projects/${projectId}/webhooks`, {
        method: 'POST',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async updateWebhook(projectId: string, webhookId: string, data: UpdateWebhookRequest): Promise<Webhook> {
      const res = await fetcher(`/projects/${projectId}/webhooks/${webhookId}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async deleteWebhook(projectId: string, webhookId: string): Promise<void> {
      await fetcher(`/projects/${projectId}/webhooks/${webhookId}`, { method: 'DELETE' })
    },

    async testWebhook(projectId: string, webhookId: string): Promise<{ success: boolean; message: string }> {
      const res = await fetcher(`/projects/${projectId}/webhooks/${webhookId}/test`, {
        method: 'POST',
      })
      return res.json()
    },

    async listWebhookEvents(projectId: string): Promise<string[]> {
      const res = await fetcher(`/projects/${projectId}/webhooks/events`)
      return res.json()
    },

    // User Profile
    async getProfile(): Promise<UserProfile> {
      const res = await fetcher('/users/me')
      return res.json()
    },

    async updateProfile(data: UpdateProfileRequest): Promise<{ message: string; displayName: string }> {
      const res = await fetcher('/users/me', {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async changePassword(data: ChangePasswordRequest): Promise<{ message: string }> {
      const res = await fetcher('/users/me/password', {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      return res.json()
    },

    async getTotpStatus(): Promise<TotpStatusResponse> {
      const res = await fetcher('/factors/totp/status')
      return res.json()
    },

    async enrollTotp(): Promise<TotpEnrollmentResponse> {
      const res = await fetcher('/factors/totp', {
        method: 'POST',
      })
      return res.json()
    },

    async verifyTotp(code: string): Promise<{ success: boolean; message: string }> {
      const res = await fetcher('/factors/totp/verify', {
        method: 'POST',
        body: JSON.stringify({ code }),
      })
      return res.json()
    },

    async removeTotp(): Promise<{ message: string }> {
      const res = await fetcher('/factors/totp', {
        method: 'DELETE',
      })
      return res.json()
    },

    async listPasskeys(): Promise<{ passkeys: Passkey[] }> {
      const res = await fetcher('/factors/passkey')
      return res.json()
    },

    async getPasskeyRegistrationOptions(): Promise<PasskeyRegistrationOptions> {
      const res = await fetcher('/factors/passkey/register/options', {
        method: 'POST',
      })
      return res.json()
    },

    async registerPasskey(payload: Record<string, unknown>): Promise<{ success: boolean; message: string; passkey: Passkey }> {
      const res = await fetcher('/factors/passkey/register', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      return res.json()
    },

    async renamePasskey(credentialId: string, label: string): Promise<{ success: boolean; message: string; passkey: Passkey }> {
      const res = await fetcher(`/factors/passkey/${encodeURIComponent(credentialId)}`, {
        method: 'PATCH',
        body: JSON.stringify({ label }),
      })
      return res.json()
    },

    async removePasskey(credentialId: string): Promise<{ message: string }> {
      const res = await fetcher(`/factors/passkey/${encodeURIComponent(credentialId)}`, {
        method: 'DELETE',
      })
      return res.json()
    },

    async deleteAccount(): Promise<{ message: string }> {
      const res = await fetcher('/users/me', { method: 'DELETE' })
      return res.json()
    },
  }
}

// ─────────────────────────────────────────────────────────────────
// Hook to get API client
// ─────────────────────────────────────────────────────────────────

export function useApiClient() {
  const { getAccessToken } = useAuth()
  return createApiClient(getAccessToken)
}
