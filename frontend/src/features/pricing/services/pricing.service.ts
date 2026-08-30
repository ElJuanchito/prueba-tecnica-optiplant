import { apiClient } from '@/lib/api-client.ts'
import {
  closePriceRequestSchema,
  createPriceListRequestSchema,
  priceListPageResponseSchema,
  priceListQuerySchema,
  priceListResponseSchema,
  pricePageResponseSchema,
  priceQuerySchema,
  priceResponseSchema,
  quoteRequestSchema,
  quoteResponseSchema,
  setPriceRequestSchema,
  updatePriceListRequestSchema,
} from '../schemas/index.ts'
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

export const pricingService = {
  async createPriceList(
    input: CreatePriceListRequest,
  ): Promise<PriceListResponse> {
    const validatedInput = createPriceListRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/pricing/price-lists', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return priceListResponseSchema.parse(raw)
  },

  async listPriceLists(
    params?: PriceListQueryParams,
  ): Promise<PriceListPageResponse> {
    const validatedParams = params
      ? priceListQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.active !== undefined) {
        searchParams.set('active', String(validatedParams.active))
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/pricing/price-lists${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return priceListPageResponseSchema.parse(raw)
  },

  async getPriceList(externalId: string): Promise<PriceListResponse> {
    const raw = await apiClient<unknown>(
      `/api/pricing/price-lists/${externalId}`,
      { method: 'GET' },
    )
    return priceListResponseSchema.parse(raw)
  },

  async updatePriceList(
    externalId: string,
    input: UpdatePriceListRequest,
  ): Promise<PriceListResponse> {
    const validatedInput = updatePriceListRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/pricing/price-lists/${externalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return priceListResponseSchema.parse(raw)
  },

  async deactivatePriceList(externalId: string): Promise<PriceListResponse> {
    const raw = await apiClient<unknown>(
      `/api/pricing/price-lists/${externalId}/deactivation`,
      { method: 'PATCH' },
    )
    return priceListResponseSchema.parse(raw)
  },

  async setPrice(
    priceListExternalId: string,
    input: SetPriceRequest,
  ): Promise<PriceResponse> {
    const validatedInput = setPriceRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/pricing/price-lists/${priceListExternalId}/prices`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return priceResponseSchema.parse(raw)
  },

  async listPrices(
    priceListExternalId: string,
    params?: PriceQueryParams,
  ): Promise<PricePageResponse> {
    const validatedParams = params ? priceQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.productExternalId !== undefined) {
        searchParams.set('productExternalId', validatedParams.productExternalId)
      }
      if (validatedParams.branchExternalId !== undefined) {
        searchParams.set('branchExternalId', validatedParams.branchExternalId)
      }
      if (validatedParams.currentOnly !== undefined) {
        searchParams.set('currentOnly', String(validatedParams.currentOnly))
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/pricing/price-lists/${priceListExternalId}/prices${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return pricePageResponseSchema.parse(raw)
  },

  async closePrice(
    priceExternalId: string,
    input?: ClosePriceRequest,
  ): Promise<PriceResponse> {
    const validatedInput = input
      ? closePriceRequestSchema.parse(input)
      : undefined
    const options: RequestInit = { method: 'PATCH' }
    if (validatedInput) {
      options.body = JSON.stringify(validatedInput)
    }
    const raw = await apiClient<unknown>(
      `/api/pricing/prices/${priceExternalId}/closure`,
      options,
    )
    return priceResponseSchema.parse(raw)
  },

  async quote(input: QuoteRequest): Promise<QuoteResponse> {
    const validatedInput = quoteRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/pricing/quotes', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return quoteResponseSchema.parse(raw)
  },
}
