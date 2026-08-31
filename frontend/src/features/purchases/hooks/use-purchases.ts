import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { purchasesService } from '../services/purchases.service.ts'
import type {
  CancellationRequest,
  CostHistoryPageResponse,
  CostHistoryQueryParams,
  CreatePurchaseOrderRequest,
  CreateSupplierRequest,
  PurchaseOrderDetailResponse,
  PurchaseOrderPageResponse,
  PurchaseOrderQueryParams,
  RegisterReceptionRequest,
  SupplierPageResponse,
  SupplierQueryParams,
  SupplierResponse,
  UpdatePurchaseOrderRequest,
  UpdateSupplierRequest,
} from '../types/index.ts'

// --- Suppliers Hooks ---

export function useSuppliers(
  params?: SupplierQueryParams,
  enabled: boolean = true,
): UseQueryResult<SupplierPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.purchases.suppliers.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => purchasesService.listSuppliers(params),
    enabled,
  })
}

export function useSupplier(
  externalId: string,
  enabled: boolean = true,
): UseQueryResult<SupplierResponse, Error> {
  return useQuery({
    queryKey: queryKeys.purchases.suppliers.detail(externalId),
    queryFn: () => purchasesService.getSupplier(externalId),
    enabled: Boolean(externalId) && enabled,
  })
}

export function useCreateSupplier(): UseMutationResult<
  SupplierResponse,
  Error,
  CreateSupplierRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateSupplierRequest) =>
      purchasesService.createSupplier(input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.all,
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useUpdateSupplier(): UseMutationResult<
  SupplierResponse,
  Error,
  { externalId: string; input: UpdateSupplierRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      purchasesService.updateSupplier(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDisableSupplier(): UseMutationResult<
  SupplierResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      purchasesService.disableSupplier(externalId),
    onSuccess: (_, externalId) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEnableSupplier(): UseMutationResult<
  SupplierResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      purchasesService.enableSupplier(externalId),
    onSuccess: (_, externalId) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.suppliers.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

// --- Purchase Orders Hooks ---

export function usePurchaseOrders(
  params?: PurchaseOrderQueryParams,
  enabled: boolean = true,
): UseQueryResult<PurchaseOrderPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.purchases.orders.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => purchasesService.listOrders(params),
    enabled,
  })
}

export function usePurchaseOrderDetail(
  externalId: string,
  enabled: boolean = true,
): UseQueryResult<PurchaseOrderDetailResponse, Error> {
  return useQuery({
    queryKey: queryKeys.purchases.orders.detail(externalId),
    queryFn: () => purchasesService.getOrderDetail(externalId),
    enabled: Boolean(externalId) && enabled,
  })
}

export function useCreatePurchaseOrder(): UseMutationResult<
  PurchaseOrderDetailResponse,
  Error,
  CreatePurchaseOrderRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreatePurchaseOrderRequest) =>
      purchasesService.createOrder(input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.all,
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useUpdatePurchaseOrder(): UseMutationResult<
  PurchaseOrderDetailResponse,
  Error,
  { externalId: string; input: UpdatePurchaseOrderRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      purchasesService.updateOrder(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useApprovePurchaseOrder(): UseMutationResult<
  PurchaseOrderDetailResponse,
  Error,
  string
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) =>
      purchasesService.approveOrder(externalId),
    onSuccess: (_, externalId) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.detail(externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useCancelPurchaseOrder(): UseMutationResult<
  PurchaseOrderDetailResponse,
  Error,
  { externalId: string; input: CancellationRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      purchasesService.cancelOrder(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useRegisterReception(): UseMutationResult<
  PurchaseOrderDetailResponse,
  Error,
  { externalId: string; input: RegisterReceptionRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      purchasesService.registerReception(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.all,
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.orders.detail(variables.externalId),
      })
      queryClient.invalidateQueries({
        queryKey: queryKeys.purchases.costHistory.all,
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

// --- Cost History Hook ---

export function useCostHistory(
  params: CostHistoryQueryParams,
  enabled: boolean = true,
): UseQueryResult<CostHistoryPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.purchases.costHistory.list(
      params as unknown as Record<string, unknown>,
    ),
    queryFn: () => purchasesService.getCostHistory(params),
    enabled: Boolean(params.productExternalId) && enabled,
  })
}
