import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, type UpsertOAuthProviderRequest } from '../lib/api'
import { projectKeys } from './useProjects'

export const authPolicyKeys = {
  methods: (projectId: string) => ['projects', projectId, 'auth-methods'] as const,
  providers: (projectId: string) => ['projects', projectId, 'oauth-providers'] as const,
}

// ─────────────────────────────────────────────────────────────────
// Queries
// ─────────────────────────────────────────────────────────────────

export function useAuthMethods(projectId: string) {
  const api = useApiClient()

  return useQuery({
    queryKey: authPolicyKeys.methods(projectId),
    queryFn: () => api.getAuthMethods(projectId),
    enabled: !!projectId,
  })
}

export function useOAuthProviders(projectId: string) {
  const api = useApiClient()

  return useQuery({
    queryKey: authPolicyKeys.providers(projectId),
    queryFn: () => api.listOAuthProviders(projectId),
    enabled: !!projectId,
  })
}

// ─────────────────────────────────────────────────────────────────
// Mutations
// ─────────────────────────────────────────────────────────────────

export function useUpdateAuthMethods(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (enabledModules: string[]) => api.updateAuthMethods(projectId, enabledModules),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: authPolicyKeys.methods(projectId) })
    },
  })
}

export function useUpsertOAuthProvider(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: UpsertOAuthProviderRequest) => api.upsertOAuthProvider(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: authPolicyKeys.providers(projectId) })
      queryClient.invalidateQueries({ queryKey: authPolicyKeys.methods(projectId) })
    },
  })
}

export function useDeleteOAuthProvider(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ provider, environment }: { provider: string; environment?: string }) =>
      api.deleteOAuthProvider(projectId, provider, environment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: authPolicyKeys.providers(projectId) })
      queryClient.invalidateQueries({ queryKey: authPolicyKeys.methods(projectId) })
    },
  })
}
