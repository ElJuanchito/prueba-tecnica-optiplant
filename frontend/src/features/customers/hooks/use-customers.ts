import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import type { SalePageResponse } from '@/features/sales/types/index.ts'
import { customerService } from '../services/customer.service.ts'
import type {
  CreateCustomerRequest,
  CustomerPageResponse,
  CustomerQueryParams,
  CustomerResponse,
  CustomerSalesHistoryQueryParams,
  EditCustomerRequest,
} from '../types/index.ts'

export function useCustomers(
  params?: CustomerQueryParams,
  enabled: boolean = true,
): UseQueryResult<CustomerPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.customers.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => customerService.list(params),
    enabled,
  })
}

export function useCustomer(
  externalId: string,
  enabled: boolean = true,
): UseQueryResult<CustomerResponse, Error> {
  return useQuery({
    queryKey: queryKeys.customers.detail(externalId),
    queryFn: () => customerService.get(externalId),
    enabled: Boolean(externalId) && enabled,
  })
}

export function useCustomerSalesHistory(
  externalId: string,
  params?: CustomerSalesHistoryQueryParams,
  enabled: boolean = true,
): UseQueryResult<SalePageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.customers.salesHistory(
      externalId,
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => customerService.getSalesHistory(externalId, params),
    enabled: Boolean(externalId) && enabled,
  })
}

export function useCreateCustomer(): UseMutationResult<
  CustomerResponse,
  Error,
  CreateCustomerRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateCustomerRequest) => customerService.create(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useUpdateCustomer(): UseMutationResult<
  CustomerResponse,
  Error,
  { externalId: string; input: EditCustomerRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      customerService.edit(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.customers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.sales.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDisableCustomer(): UseMutationResult<
  CustomerResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) => customerService.disable(externalId),
    onSuccess: (_, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.customers.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEnableCustomer(): UseMutationResult<
  CustomerResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) => customerService.enable(externalId),
    onSuccess: (_, externalId) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.customers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.customers.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
