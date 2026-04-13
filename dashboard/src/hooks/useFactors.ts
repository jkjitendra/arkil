import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApiClient } from '../lib/api'

export const factorKeys = {
  totpStatus: ['factors', 'totp', 'status'] as const,
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
