import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { productUnitService } from '../services/product-unit.service.ts'
import type {
  ProductUnitItemResponse,
  ProductUnitListResponse,
  UnitRequestInput,
} from '../types/product-unit.types.ts'

export function useProductUnits(
  productExternalId: string,
): UseQueryResult<ProductUnitListResponse, Error> {
  return useQuery({
    queryKey: queryKeys.productUnits.byProduct(productExternalId),
    queryFn: () => productUnitService.listUnits(productExternalId),
    enabled: Boolean(productExternalId),
  })
}

export function useAddProductUnit(): UseMutationResult<
  ProductUnitItemResponse,
  Error,
  { productExternalId: string; input: UnitRequestInput }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ productExternalId, input }) =>
      productUnitService.addUnit(productExternalId, input),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.productUnits.byProduct(variables.productExternalId),
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.detail(variables.productExternalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useReplaceProductUnit(): UseMutationResult<
  ProductUnitItemResponse,
  Error,
  {
    productExternalId: string
    unitExternalId: string
    input: UnitRequestInput
  }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ productExternalId, unitExternalId, input }) =>
      productUnitService.replaceUnit(productExternalId, unitExternalId, input),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.productUnits.byProduct(variables.productExternalId),
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.detail(variables.productExternalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDeleteProductUnit(): UseMutationResult<
  void,
  Error,
  { productExternalId: string; unitExternalId: string }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ productExternalId, unitExternalId }) =>
      productUnitService.deleteUnit(productExternalId, unitExternalId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.productUnits.byProduct(variables.productExternalId),
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.detail(variables.productExternalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
