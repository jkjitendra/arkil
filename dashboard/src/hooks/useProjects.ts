import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, type Project, type CreateProjectRequest, type UpdateProjectRequest } from '../lib/api'

// Query keys factory
export const projectKeys = {
  all: ['projects'] as const,
  deleted: ['projects', 'deleted'] as const,
  detail: (id: string) => ['projects', id] as const,
  keys: (projectId: string) => ['projects', projectId, 'keys'] as const,
}

// ─────────────────────────────────────────────────────────────────
// Queries
// ─────────────────────────────────────────────────────────────────

export function useProjects() {
  const api = useApiClient()

  return useQuery({
    queryKey: projectKeys.all,
    queryFn: () => api.listProjects(),
  })
}

export function useProject(projectId: string) {
  const api = useApiClient()

  return useQuery({
    queryKey: projectKeys.detail(projectId),
    queryFn: () => api.getProject(projectId),
    enabled: !!projectId,
  })
}

// ─────────────────────────────────────────────────────────────────
// Mutations
// ─────────────────────────────────────────────────────────────────

export function useCreateProject() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CreateProjectRequest) => api.createProject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: projectKeys.all })
    },
  })
}

export function useUpdateProject(projectId: string) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: UpdateProjectRequest) => api.updateProject(projectId, data),
    onSuccess: (updatedProject) => {
      // Update cache directly
      queryClient.setQueryData(projectKeys.detail(projectId), updatedProject)
      // Also invalidate list to refresh any derived data
      queryClient.invalidateQueries({ queryKey: projectKeys.all })
    },
  })
}

export function useDeleteProject() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (projectId: string) => api.deleteProject(projectId),
    onSuccess: (_, projectId) => {
      // Remove from cache
      queryClient.removeQueries({ queryKey: projectKeys.detail(projectId) })
      queryClient.invalidateQueries({ queryKey: projectKeys.all })
      queryClient.invalidateQueries({ queryKey: projectKeys.deleted })
    },
  })
}

export function useDeletedProjects() {
  const api = useApiClient()

  return useQuery({
    queryKey: projectKeys.deleted,
    queryFn: () => api.listDeletedProjects(),
  })
}

export function useRestoreProject() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (projectId: string) => api.restoreProject(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: projectKeys.all })
      queryClient.invalidateQueries({ queryKey: projectKeys.deleted })
    },
  })
}
