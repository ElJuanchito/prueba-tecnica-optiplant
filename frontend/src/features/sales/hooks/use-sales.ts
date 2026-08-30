import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { saleService } from '../services/sale.service.ts'
import type {
  CancellationRequest,
  RegisterSaleRequest,
  SaleDetailResponse,
  SalePageResponse,
  SaleQueryParams,
} from '../types/index.ts'

export function useSales(
  params?: SaleQueryParams,
  enabled: boolean = true,
): UseQueryResult<SalePageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.sales.list((params ?? {}) as Record<string, unknown>),
    queryFn: () => saleService.list(params),
    enabled,
  })
}

export function useSaleDetail(
  externalId: string,
  enabled: boolean = true,
): UseQueryResult<SaleDetailResponse, Error> {
  return useQuery({
    queryKey: queryKeys.sales.detail(externalId),
    queryFn: () => saleService.getDetail(externalId),
    enabled: Boolean(externalId) && enabled,
  })
}

export function useSaleByInvoice(
  invoiceNumber: string,
  enabled: boolean = true,
): UseQueryResult<SaleDetailResponse, Error> {
  return useQuery({
    queryKey: queryKeys.sales.byInvoice(invoiceNumber),
    queryFn: () => saleService.getByInvoiceNumber(invoiceNumber),
    enabled: Boolean(invoiceNumber) && enabled,
  })
}

export function useRegisterSale(): UseMutationResult<
  SaleDetailResponse,
  Error,
  RegisterSaleRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: RegisterSaleRequest) => saleService.register(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.sales.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useCancelSale(): UseMutationResult<
  SaleDetailResponse,
  Error,
  { externalId: string; input: CancellationRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      saleService.cancel(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.sales.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.sales.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
