import { apiClient } from '@/lib/api-client.ts'
import {
  approvalRequestSchema,
  dispatchRequestSchema,
  reasonRequestSchema,
  receiptRequestSchema,
  requestTransferRequestSchema,
  transferDetailResponseSchema,
  transferPageResponseSchema,
  transferQuerySchema,
} from '../schemas/index.ts'
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

export const transferService = {
  async request(
    input: RequestTransferRequest,
  ): Promise<TransferDetailResponse> {
    const validatedInput = requestTransferRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/transfers', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return transferDetailResponseSchema.parse(raw)
  },

  async list(params?: TransferQueryParams): Promise<TransferPageResponse> {
    const validatedParams = params
      ? transferQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.status !== undefined) {
        searchParams.set('status', validatedParams.status)
      }
      if (validatedParams.direction !== undefined) {
        searchParams.set('direction', validatedParams.direction)
      }
      if (validatedParams.from !== undefined) {
        searchParams.set('from', validatedParams.from)
      }
      if (validatedParams.to !== undefined) {
        searchParams.set('to', validatedParams.to)
      }
      if (validatedParams.sort !== undefined) {
        searchParams.set('sort', validatedParams.sort)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/transfers${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return transferPageResponseSchema.parse(raw)
  },

  async getDetail(externalId: string): Promise<TransferDetailResponse> {
    const raw = await apiClient<unknown>(`/api/transfers/${externalId}`, {
      method: 'GET',
    })
    return transferDetailResponseSchema.parse(raw)
  },

  async approve(
    externalId: string,
    input: ApprovalRequest,
  ): Promise<TransferDetailResponse> {
    const validatedInput = approvalRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/transfers/${externalId}/approval`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return transferDetailResponseSchema.parse(raw)
  },

  async reject(
    externalId: string,
    input: ReasonRequest,
  ): Promise<TransferDetailResponse> {
    const validatedInput = reasonRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/transfers/${externalId}/rejection`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return transferDetailResponseSchema.parse(raw)
  },

  async dispatch(
    externalId: string,
    input: DispatchRequest,
  ): Promise<TransferDetailResponse> {
    const validatedInput = dispatchRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/transfers/${externalId}/dispatch`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return transferDetailResponseSchema.parse(raw)
  },

  async receive(
    externalId: string,
    input: ReceiptRequest,
  ): Promise<TransferDetailResponse> {
    const validatedInput = receiptRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/transfers/${externalId}/receipt`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return transferDetailResponseSchema.parse(raw)
  },

  async cancel(
    externalId: string,
    input: ReasonRequest,
  ): Promise<TransferDetailResponse> {
    const validatedInput = reasonRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/transfers/${externalId}/cancellation`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return transferDetailResponseSchema.parse(raw)
  },
}
