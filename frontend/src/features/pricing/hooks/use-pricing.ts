import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { pricingService } from '../services/pricing.service.ts'
import type {
  ClosePriceRequest,
  CreatePriceListRequest,
  PriceListPageResponse,
  PriceListQueryParams,
  PriceListResponse,
  PricePageResponse,
  PriceQueryParams,
  PriceResponse,
  QuoteRequest,
  QuoteResponse,
  SetPriceRequest,
  UpdatePriceListRequest,
} from '../types/index.ts'

export function usePriceLists(
  params?: PriceListQueryParams,
  enabled: boolean = true,
): UseQueryResult<PriceListPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.pricing.priceLists.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => pricingService.listPriceLists(params),
    enabled,
  })
}

export function usePriceList(
  externalId: string,
  enabled: boolean = true,
): UseQueryResult<PriceListResponse, Error> {
  return useQuery({
    queryKey: queryKeys.pricing.priceLists.detail(externalId),
    queryFn: () => pricingService.getPriceList(externalId),
    enabled: Boolean(externalId) && enabled,
  })
}

export function usePrices(
  priceListExternalId: string,
  params?: PriceQueryParams,
  enabled: boolean = true,
): UseQueryResult<PricePageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.pricing.prices.byPriceList(
      priceListExternalId,
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => pricingService.listPrices(priceListExternalId, params),
    enabled: Boolean(priceListExternalId) && enabled,
  })
}

export function useCreatePriceList(): UseMutationResult<
  PriceListResponse,
  Error,
  CreatePriceListRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreatePriceListRequest) =>
      pricingService.createPriceList(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.pricing.priceLists.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useUpdatePriceList(): UseMutationResult<
  PriceListResponse,
  Error,
  { externalId: string; input: UpdatePriceListRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      pricingService.updatePriceList(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.pricing.priceLists.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.pricing.priceLists.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDeactivatePriceList(): UseMutationResult<
  PriceListResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      pricingService.deactivatePriceList(externalId),
    onSuccess: (_, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.pricing.priceLists.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.pricing.priceLists.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useSetPrice(): UseMutationResult<
  PriceResponse,
  Error,
  { priceListExternalId: string; input: SetPriceRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ priceListExternalId, input }) =>
      pricingService.setPrice(priceListExternalId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.pricing.prices.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.pricing.priceLists.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useClosePrice(): UseMutationResult<
  PriceResponse,
  Error,
  { priceExternalId: string; input?: ClosePriceRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ priceExternalId, input }) =>
      pricingService.closePrice(priceExternalId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.pricing.prices.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function usePricingQuote(): UseMutationResult<
  QuoteResponse,
  Error,
  QuoteRequest
> {
  return useMutation({
    mutationFn: (input: QuoteRequest) => pricingService.quote(input),
  })
}
