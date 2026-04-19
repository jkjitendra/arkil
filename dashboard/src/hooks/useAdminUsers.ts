import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApiClient, type UpdateAdminUserRequest } from '@/lib/api'

export const adminUserKeys = {
  all: ['admin-users'] as const,
  list: (page: number, size: number) => ['admin-users', page, size] as const,
  detail: (userId: string) => ['admin-users', userId] as const,
}

export function useAdminUsers(page: number, size: number, enabled = true) {
  const api = useApiClient()

  return useQuery({
    queryKey: adminUserKeys.list(page, size),
    queryFn: () => api.listAdminUsers({ page, size }),
    enabled,
  })
}

export function useAdminUser(userId: string | null, enabled = true) {
  const api = useApiClient()

  return useQuery({
    queryKey: adminUserKeys.detail(userId || 'unknown'),
    queryFn: () => api.getAdminUser(userId!),
    enabled: !!userId && enabled,
  })
}

export function useUpdateAdminUser() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, data }: { userId: string; data: UpdateAdminUserRequest }) =>
      api.updateAdminUser(userId, data),
    onSuccess: (updatedUser) => {
      queryClient.setQueryData(adminUserKeys.detail(updatedUser.id), updatedUser)
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })
}

export function useBlockAdminUser() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, reason }: { userId: string; reason?: string }) =>
      api.blockAdminUser(userId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })
}

export function useUnblockAdminUser() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (userId: string) => api.unblockAdminUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })
}

export function useDeleteAdminUser() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (userId: string) => api.deleteAdminUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })
}
