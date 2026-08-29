import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { alertService } from '../services/alert.service.ts'
import type {
  AlertPageResponse,
  AlertQueryParams,
  AlertResponse,
} from '../types/index.ts'

export function useAlerts(
  params?: AlertQueryParams,
): UseQueryResult<AlertPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.notifications.alerts.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => alertService.listAlerts(params),
  })
}

export function useResolveAlert(): UseMutationResult<
  AlertResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) => alertService.resolveAlert(externalId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
