import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApiClient } from '../lib/api'

export const factorKeys = {
  totpStatus: ['factors', 'totp', 'status'] as const,
  passkeys: ['factors', 'passkeys'] as const,
}

export function useTotpStatus() {
  const api = useApiClient()

  return useQuery({
    queryKey: factorKeys.totpStatus,
    queryFn: () => api.getTotpStatus(),
  })
}

export function useEnrollTotp() {
  const api = useApiClient()

  return useMutation({
    mutationFn: () => api.enrollTotp(),
  })
}

export function useVerifyTotp() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (code: string) => api.verifyTotp(code),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: factorKeys.totpStatus })
    },
  })
}

export function useRemoveTotp() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => api.removeTotp(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: factorKeys.totpStatus })
    },
  })
}

export function usePasskeys() {
  const api = useApiClient()

  return useQuery({
    queryKey: factorKeys.passkeys,
    queryFn: () => api.listPasskeys(),
  })
}

export function useCreatePasskey() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.registerPasskey(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: factorKeys.passkeys })
    },
  })
}

export function useRenamePasskey() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ credentialId, label }: { credentialId: string; label: string }) =>
      api.renamePasskey(credentialId, label),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: factorKeys.passkeys })
    },
  })
}

export function useRemovePasskey() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (credentialId: string) => api.removePasskey(credentialId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: factorKeys.passkeys })
    },
  })
}
