import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { logisticsService } from '../services/logistics.service.ts'
import type {
  ActiveTransferPageResponse,
  ActiveTransferQueryParams,
  CompliancePageResponse,
  ComplianceQueryParams,
  CreateRouteRequest,
  RoutePageResponse,
  RouteQueryParams,
  RouteResponse,
  UpdateRouteRequest,
} from '../types/index.ts'

export function useRoutes(
  params?: RouteQueryParams,
  enabled: boolean = true,
): UseQueryResult<RoutePageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.logistics.routes.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => logisticsService.listRoutes(params),
    enabled,
  })
}

export function useCreateRoute(): UseMutationResult<
  RouteResponse,
  Error,
  CreateRouteRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateRouteRequest) =>
      logisticsService.createRoute(input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.logistics.routes.all,
      })
    },
  })
}

export function useUpdateRoute(): UseMutationResult<
  RouteResponse,
  Error,
  { externalId: string; input: UpdateRouteRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      logisticsService.updateRoute(externalId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.logistics.routes.all,
      })
    },
  })
}

export function useDeactivateRoute(): UseMutationResult<
  RouteResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      logisticsService.deactivateRoute(externalId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.logistics.routes.all,
      })
    },
  })
}

export function useActiveTransfers(
  params?: ActiveTransferQueryParams,
  enabled: boolean = true,
): UseQueryResult<ActiveTransferPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.logistics.monitor.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => logisticsService.listActiveTransfers(params),
    enabled,
  })
}

export function useComplianceReport(
  params: ComplianceQueryParams,
  enabled: boolean = true,
): UseQueryResult<CompliancePageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.logistics.compliance.list(
      params as unknown as Record<string, unknown>,
    ),
    queryFn: () => logisticsService.getComplianceReport(params),
    enabled: Boolean(params.from && params.to) && enabled,
  })
}
