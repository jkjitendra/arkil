// OIDC Configuration for Arkil Dashboard
export const authConfig = {
  authority: 'http://localhost:8080', // Arkil Authorization Server
  client_id: 'arkil-dashboard',
  redirect_uri: 'http://localhost:5173/callback',
  post_logout_redirect_uri: 'http://localhost:5173/',
  silent_redirect_uri: 'http://localhost:5173/silent-refresh',
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

// API base URL
export const API_BASE_URL = '/api'
