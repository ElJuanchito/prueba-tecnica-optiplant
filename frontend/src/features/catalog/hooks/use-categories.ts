import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { categoryService } from '../services/category.service.ts'
import type {
  CategoryPageResponse,
  CategoryQueryParams,
  CategoryResponse,
  CreateCategoryInput,
  EditCategoryInput,
} from '../types/category.types.ts'

export function useCategories(
  params?: CategoryQueryParams,
): UseQueryResult<CategoryPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.categories.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => categoryService.listCategories(params),
  })
}

export function useCategory(
  externalId: string,
): UseQueryResult<CategoryResponse, Error> {
  return useQuery({
    queryKey: queryKeys.categories.detail(externalId),
    queryFn: () => categoryService.getCategory(externalId),
    enabled: Boolean(externalId),
  })
}

export function useCreateCategory(): UseMutationResult<
  CategoryResponse,
  Error,
  CreateCategoryInput
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateCategoryInput) =>
      categoryService.createCategory(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEditCategory(): UseMutationResult<
  CategoryResponse,
  Error,
  { externalId: string; input: EditCategoryInput }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      categoryService.editCategory(externalId, input),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.categories.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDisableCategory(): UseMutationResult<
  CategoryResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      categoryService.disableCategory(externalId),
    onSuccess: (_data, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.categories.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEnableCategory(): UseMutationResult<
  CategoryResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      categoryService.enableCategory(externalId),
    onSuccess: (_data, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.categories.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
