import { apiClient } from '@/lib/api-client.ts'
import {
  cancellationRequestSchema,
  registerSaleRequestSchema,
  saleDetailResponseSchema,
  salePageResponseSchema,
  saleQuerySchema,
} from '../schemas/index.ts'
import type {
  CancellationRequest,
  RegisterSaleRequest,
  SaleDetailResponse,
  SalePageResponse,
  SaleQueryParams,
} from '../types/index.ts'

export const saleService = {
  async register(input: RegisterSaleRequest): Promise<SaleDetailResponse> {
    const validatedInput = registerSaleRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/sales', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return saleDetailResponseSchema.parse(raw)
  },

  async list(params?: SaleQueryParams): Promise<SalePageResponse> {
    const validatedParams = params ? saleQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.status !== undefined) {
        searchParams.set('status', validatedParams.status)
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
    const path = `/api/sales${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return salePageResponseSchema.parse(raw)
  },

  async getDetail(externalId: string): Promise<SaleDetailResponse> {
    const raw = await apiClient<unknown>(`/api/sales/${externalId}`, {
      method: 'GET',
    })
    return saleDetailResponseSchema.parse(raw)
  },

  async getByInvoiceNumber(invoiceNumber: string): Promise<SaleDetailResponse> {
    const raw = await apiClient<unknown>(
      `/api/sales/by-invoice/${encodeURIComponent(invoiceNumber)}`,
      { method: 'GET' },
    )
    return saleDetailResponseSchema.parse(raw)
  },

  async cancel(
    externalId: string,
    input: CancellationRequest,
  ): Promise<SaleDetailResponse> {
    const validatedInput = cancellationRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/sales/${externalId}/cancellation`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return saleDetailResponseSchema.parse(raw)
  },
}
