import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, type ApiKey, type CreateKeyRequest, type KeyPairResult } from '../lib/api'
import { projectKeys } from './useProjects'

// ─────────────────────────────────────────────────────────────────
// Queries
// ─────────────────────────────────────────────────────────────────

export function useProjectKeys(projectId: string) {
  const api = useApiClient()

  return useQuery({
    queryKey: projectKeys.keys(projectId),
    queryFn: () => api.listKeys(projectId),
    enabled: !!projectId,
  })
}

// ─────────────────────────────────────────────────────────────────
// Mutations
// ─────────────────────────────────────────────────────────────────

interface CreateKeyResult {
  keyPair: KeyPairResult
  projectId: string
}

export function useCreateKey(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (data: CreateKeyRequest): Promise<CreateKeyResult> => {
      const keyPair = await api.createKey(projectId, data)
      return { keyPair, projectId }
    },
    onSuccess: ({ projectId }) => {
      queryClient.invalidateQueries({ queryKey: projectKeys.keys(projectId) })
    },
  })
}

interface RotateKeyResult {
  keyPair: KeyPairResult
  projectId: string
}

export function useRotateKey(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (keyId: string): Promise<RotateKeyResult> => {
      const keyPair = await api.rotateKey(projectId, keyId)
      return { keyPair, projectId }
    },
    onSuccess: ({ projectId }) => {
      queryClient.invalidateQueries({ queryKey: projectKeys.keys(projectId) })
    },
  })
}

interface RevokeKeyParams {
  keyId: string
  reason?: string
}

export function useRevokeKey(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ keyId, reason }: RevokeKeyParams) => {
      await api.revokeKey(projectId, keyId, reason)
      return { projectId }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: projectKeys.keys(projectId) })
    },
  })
}
