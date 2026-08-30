import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { transferService } from '../services/transfer.service.ts'
import type {
  ApprovalRequest,
  DispatchRequest,
  ReasonRequest,
  ReceiptRequest,
  RequestTransferRequest,
  TransferDetailResponse,
  TransferPageResponse,
  TransferQueryParams,
} from '../types/index.ts'

export function useTransfers(
  params?: TransferQueryParams,
  enabled: boolean = true,
): UseQueryResult<TransferPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.transfers.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => transferService.list(params),
    enabled,
  })
}

export function useTransferDetail(
  externalId: string,
  enabled: boolean = true,
): UseQueryResult<TransferDetailResponse, Error> {
  return useQuery({
    queryKey: queryKeys.transfers.detail(externalId),
    queryFn: () => transferService.getDetail(externalId),
    enabled: Boolean(externalId) && enabled,
  })
}

export function useRequestTransfer(): UseMutationResult<
  TransferDetailResponse,
  Error,
  RequestTransferRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: RequestTransferRequest) =>
      transferService.request(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.transfers.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.logistics.all })
    },
  })
}

export function useApproveTransfer(): UseMutationResult<
  TransferDetailResponse,
  Error,
  { externalId: string; input: ApprovalRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      transferService.approve(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.transfers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.transfers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useRejectTransfer(): UseMutationResult<
  TransferDetailResponse,
  Error,
  { externalId: string; input: ReasonRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      transferService.reject(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.transfers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.transfers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDispatchTransfer(): UseMutationResult<
  TransferDetailResponse,
  Error,
  { externalId: string; input: DispatchRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      transferService.dispatch(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.transfers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.transfers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.logistics.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useReceiveTransfer(): UseMutationResult<
  TransferDetailResponse,
  Error,
  { externalId: string; input: ReceiptRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      transferService.receive(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.transfers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.transfers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.logistics.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useCancelTransfer(): UseMutationResult<
  TransferDetailResponse,
  Error,
  { externalId: string; input: ReasonRequest }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      transferService.cancel(externalId, input),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.transfers.all })
      queryClient.invalidateQueries({
        queryKey: queryKeys.transfers.detail(variables.externalId),
      })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
