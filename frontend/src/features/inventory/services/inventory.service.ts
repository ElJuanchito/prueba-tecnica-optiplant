import { apiClient } from '@/lib/api-client.ts'
import {
  adjustStockRequestSchema,
  kardexPageResponseSchema,
  kardexQuerySchema,
  movementReceiptResponseSchema,
  networkAvailabilityResponseSchema,
  setThresholdRequestSchema,
  stockPageResponseSchema,
  stockQuerySchema,
  thresholdResponseSchema,
  writeOffRequestSchema,
} from '../schemas/index.ts'
import type {
  AdjustStockRequest,
  KardexPageResponse,
  KardexQueryParams,
  MovementReceiptResponse,
  NetworkAvailabilityResponse,
  SetThresholdRequest,
  StockPageResponse,
  StockQueryParams,
  ThresholdResponse,
  WriteOffRequest,
} from '../types/index.ts'

export const inventoryService = {
  async listStock(params?: StockQueryParams): Promise<StockPageResponse> {
    const validatedParams = params ? stockQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.productExternalId !== undefined) {
        searchParams.set('productExternalId', validatedParams.productExternalId)
      }
      if (validatedParams.belowThreshold !== undefined) {
        searchParams.set('belowThreshold', String(validatedParams.belowThreshold))
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
    const path = `/api/inventory/stock${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return stockPageResponseSchema.parse(raw)
  },

  async getNetworkAvailability(
    productExternalId: string,
  ): Promise<NetworkAvailabilityResponse> {
    const raw = await apiClient<unknown>(
      `/api/inventory/stock/${productExternalId}/network`,
      { method: 'GET' },
    )
    return networkAvailabilityResponseSchema.parse(raw)
  },

  async adjustStock(
    input: AdjustStockRequest,
  ): Promise<MovementReceiptResponse> {
    const validatedInput = adjustStockRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/inventory/adjustments', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return movementReceiptResponseSchema.parse(raw)
  },

  async writeOffStock(
    input: WriteOffRequest,
  ): Promise<MovementReceiptResponse> {
    const validatedInput = writeOffRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/inventory/write-offs', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return movementReceiptResponseSchema.parse(raw)
  },

  async setThreshold(
    productExternalId: string,
    input: SetThresholdRequest,
  ): Promise<ThresholdResponse> {
    const validatedInput = setThresholdRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/inventory/stock/${productExternalId}/threshold`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return thresholdResponseSchema.parse(raw)
  },

  async listKardex(params?: KardexQueryParams): Promise<KardexPageResponse> {
    const validatedParams = params ? kardexQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.productExternalId !== undefined) {
        searchParams.set('productExternalId', validatedParams.productExternalId)
      }
      if (validatedParams.movementType !== undefined) {
        searchParams.set('movementType', validatedParams.movementType)
      }
      if (validatedParams.from !== undefined) {
        searchParams.set('from', validatedParams.from)
      }
      if (validatedParams.to !== undefined) {
        searchParams.set('to', validatedParams.to)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/inventory/kardex${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return kardexPageResponseSchema.parse(raw)
  },
}
