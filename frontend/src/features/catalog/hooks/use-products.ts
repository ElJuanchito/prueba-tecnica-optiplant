import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { productService } from '../services/product.service.ts'
import type {
  CreateProductInput,
  EditProductInput,
  ProductDetailResponse,
  ProductPageResponse,
  ProductQueryParams,
} from '../types/product.types.ts'

export function useProducts(
  params?: ProductQueryParams,
  enabled: boolean = true,
): UseQueryResult<ProductPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.products.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => productService.listProducts(params),
    enabled,
  })
}

export function useProduct(
  externalId: string,
): UseQueryResult<ProductDetailResponse, Error> {
  return useQuery({
    queryKey: queryKeys.products.detail(externalId),
    queryFn: () => productService.getProduct(externalId),
    enabled: Boolean(externalId),
  })
}

export function useCreateProduct(): UseMutationResult<
  ProductDetailResponse,
  Error,
  CreateProductInput
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateProductInput) =>
      productService.createProduct(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEditProduct(): UseMutationResult<
  ProductDetailResponse,
  Error,
  { externalId: string; input: EditProductInput }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      productService.editProduct(externalId, input),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDisableProduct(): UseMutationResult<
  ProductDetailResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      productService.disableProduct(externalId),
    onSuccess: (_data, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEnableProduct(): UseMutationResult<
  ProductDetailResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      productService.enableProduct(externalId),
    onSuccess: (_data, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
