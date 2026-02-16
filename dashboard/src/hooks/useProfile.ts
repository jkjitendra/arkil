import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, type UpdateProfileRequest, type ChangePasswordRequest } from '../lib/api'

export const profileKeys = {
  me: ['profile', 'me'] as const,
}

export function useProfile() {
  const api = useApiClient()

  return useQuery({
    queryKey: profileKeys.me,
    queryFn: () => api.getProfile(),
  })
}

export function useUpdateProfile() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => api.updateProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: profileKeys.me })
    },
  })
}

export function useChangePassword() {
  const api = useApiClient()

  return useMutation({
    mutationFn: (data: ChangePasswordRequest) => api.changePassword(data),
  })
}

export function useDeleteAccount() {
  const api = useApiClient()

  return useMutation({
    mutationFn: () => api.deleteAccount(),
  })
}
