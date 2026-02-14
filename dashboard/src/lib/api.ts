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
  clientSecret: string
  scopes?: string
  environment?: string
  enabled?: boolean
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
  }
}

// ─────────────────────────────────────────────────────────────────
// Hook to get API client
// ─────────────────────────────────────────────────────────────────

export function useApiClient() {
  const { getAccessToken } = useAuth()
  return createApiClient(getAccessToken)
}
