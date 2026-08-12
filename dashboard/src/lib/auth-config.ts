function normalizeUrl(value: string, variableName: string): string {
  const normalized = value.trim().replace(/\/$/, '')
  if (!/^https?:\/\//.test(normalized)) {
    throw new Error(`${variableName} must be an absolute http(s) URL`)
  }
  return normalized
}

const configuredAuthServerUrl = import.meta.env.VITE_AUTH_SERVER_URL

if (!configuredAuthServerUrl) {
  throw new Error('VITE_AUTH_SERVER_URL must be set for a dashboard build')
}

export const AUTH_SERVER_URL = normalizeUrl(configuredAuthServerUrl, 'VITE_AUTH_SERVER_URL')
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || `${AUTH_SERVER_URL}/api/v1`
const dashboardOrigin = window.location.origin

// OIDC Configuration for Arkil Dashboard. The callback origin is taken from
// the browser so a build cannot accidentally retain a developer's localhost URL.
export const authConfig = {
  authority: AUTH_SERVER_URL,
  client_id: 'arkil-dashboard',
  redirect_uri: `${dashboardOrigin}/callback`,
  post_logout_redirect_uri: `${dashboardOrigin}/`,
  silent_redirect_uri: `${dashboardOrigin}/silent-refresh`,
  scope: 'openid profile email arkil:admin',
  response_type: 'code',

  // Token storage
  userStore: undefined, // Will use sessionStorage by default

  // Automatic token refresh
  automaticSilentRenew: true,

  // Check session (if auth server supports)
  monitorSession: false,

  // Handle tokens in hash vs query
  response_mode: 'query' as const,
}
