import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { inventoryService } from '../services/inventory.service.ts'
import type {
  AdjustStockRequest,
  KardexPageResponse,
  KardexQueryParams,
  MovementReceiptResponse,
  NetworkAvailabilityResponse,
  SetThresholdRequest,
  StockPageResponse,
  StockQueryParams,
  ThresholdResponse,
  WriteOffRequest,
} from '../types/index.ts'

export function useStock(
  params?: StockQueryParams,
  enabled: boolean = true,
): UseQueryResult<StockPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.inventory.stock.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => inventoryService.listStock(params),
    enabled,
  })
}

export function useNetworkAvailability(
  productExternalId: string,
  enabled: boolean = true,
): UseQueryResult<NetworkAvailabilityResponse, Error> {
  return useQuery({
    queryKey: queryKeys.inventory.stock.network(productExternalId),
    queryFn: () => inventoryService.getNetworkAvailability(productExternalId),
    enabled: Boolean(productExternalId) && enabled,
  })
}

export function useAdjustStock(): UseMutationResult<
  MovementReceiptResponse,
  Error,
  AdjustStockRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: AdjustStockRequest) =>
      inventoryService.adjustStock(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
    },
  })
}

export function useWriteOffStock(): UseMutationResult<
  MovementReceiptResponse,
  Error,
  WriteOffRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: WriteOffRequest) =>
      inventoryService.writeOffStock(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
    },
  })
}

export function useSetThreshold(): UseMutationResult<
  ThresholdResponse,
  Error,
  { productExternalId: string; input: SetThresholdRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ productExternalId, input }) =>
      inventoryService.setThreshold(productExternalId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
    },
  })
}

export function useKardex(
  params?: KardexQueryParams,
): UseQueryResult<KardexPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.inventory.kardex.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => inventoryService.listKardex(params),
  })
}
